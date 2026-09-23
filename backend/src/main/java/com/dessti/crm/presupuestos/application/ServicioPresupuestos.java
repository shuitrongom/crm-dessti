package com.dessti.crm.presupuestos.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.presupuestos.adapter.out.persistence.PresupuestoRepository;
import com.dessti.crm.presupuestos.domain.CalculoVariacionPresupuesto;
import com.dessti.crm.presupuestos.domain.Presupuesto;
import com.dessti.crm.presupuestos.domain.ResultadoVariacion;
import com.dessti.crm.presupuestos.domain.TipoPresupuesto;

/**
 * Servicio de aplicacion que gobierna los {@link Presupuesto} por area y periodo y el
 * calculo de su variacion frente al ejercicio real (Req 62). Replica el patron
 * establecido por {@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones (Req 62)</h2>
 * <ul>
 *   <li><strong>crearPresupuesto (Req 62.1, 62.5):</strong> da de alta un Presupuesto
 *       con montos estimados de ingresos y/o egresos; rechaza el duplicado de
 *       {@code (area, periodo)} con 409 (doble defensa: pre-check + indice unico de
 *       V40); audita.</li>
 *   <li><strong>actualizarPresupuesto (Req 62.5):</strong> modifica los montos
 *       estimados; audita el estado anterior y el nuevo.</li>
 *   <li><strong>consultarVariacion (Req 62.2, 62.3, 62.6, 62.7):</strong> carga el
 *       Presupuesto, obtiene el real via {@link RealEjercidoPort} (agregacion de solo
 *       lectura, sin mutacion), calcula la variacion de ingresos y egresos con
 *       {@link CalculoVariacionPresupuesto} (Property 36) y marca las desviaciones que
 *       superan el umbral configurable (Req 62.3). Es de solo lectura.</li>
 *   <li><strong>consultar / listar (Req 62.4, 23.3):</strong> consulta puntual (404 +
 *       auditoria del intento si no es accesible) y listado paginado (20/100) con
 *       filtros por area y periodo.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 62.5)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). Las operaciones de escritura se registran via {@link AuditoriaPort} con
 * el actor derivado del contexto.</p>
 */
@Service
public class ServicioPresupuestos {

    /** Tipo de recurso de auditoria/RBAC del Presupuesto. */
    static final String RECURSO_PRESUPUESTO = "presupuesto";

    private final PresupuestoRepository presupuestoRepository;
    private final RealEjercidoPort realEjercido;
    private final PresupuestosProperties propiedades;
    private final AuditoriaPort auditoria;

    public ServicioPresupuestos(PresupuestoRepository presupuestoRepository,
                                RealEjercidoPort realEjercido,
                                PresupuestosProperties propiedades,
                                AuditoriaPort auditoria) {
        this.presupuestoRepository = presupuestoRepository;
        this.realEjercido = realEjercido;
        this.propiedades = propiedades;
        this.auditoria = auditoria;
    }

    /**
     * Crea un Presupuesto por area y periodo con montos estimados de ingresos y/o
     * egresos (Req 62.1). Rechaza el duplicado de {@code (area, periodo)} con 409.
     *
     * @param command datos del Presupuesto a crear.
     * @return el DTO del Presupuesto creado.
     * @throws ReglaNegocioException      si faltan datos o los montos son invalidos (422).
     * @throws ConflictoUnicidadException si ya existe un Presupuesto para ese area y
     *         periodo (409, Req 62.1).
     */
    @Transactional
    public PresupuestoDto crearPresupuesto(CrearPresupuestoCommand command) {
        String actor = actorActual();
        if (command == null) {
            throw new ReglaNegocioException("Los datos del Presupuesto son obligatorios.");
        }

        Presupuesto presupuesto = Presupuesto.crear(
                command.area(), command.periodo(),
                command.ingresosEstimados(), command.egresosEstimados(), actor);

        // Pre-verificacion de unicidad de negocio (Req 62.1).
        if (presupuestoRepository.existsByAreaAndPeriodo(
                presupuesto.getArea(), presupuesto.getPeriodo())) {
            throw new ConflictoUnicidadException(
                    "ya existe un Presupuesto para el area y periodo indicados");
        }

        Presupuesto guardado;
        try {
            // saveAndFlush para materializar la violacion del indice unico
            // (tenant_id, area, periodo) de V40 dentro de este try (segunda capa de
            // defensa ante concurrencia).
            guardado = presupuestoRepository.saveAndFlush(presupuesto);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoUnicidadException(
                    "ya existe un Presupuesto para el area y periodo indicados");
        }

        auditar(actor, "crear", guardado.getId(),
                "creado Presupuesto [area=" + guardado.getArea()
                        + ", periodo=" + guardado.getPeriodo()
                        + ", ingresos_estimados=" + guardado.getIngresosEstimados()
                        + ", egresos_estimados=" + guardado.getEgresosEstimados() + "]",
                null, resumen(guardado));
        return PresupuestoDto.de(guardado);
    }

    /**
     * Actualiza los montos estimados de un Presupuesto (Req 62.5). El area y el
     * periodo son la identidad de negocio y no se modifican.
     *
     * @param presupuestoId     identificador del Presupuesto.
     * @param ingresosEstimados nuevos ingresos estimados; no negativo.
     * @param egresosEstimados  nuevos egresos estimados; no negativo.
     * @return el DTO del Presupuesto actualizado.
     * @throws RecursoNoEncontradoException si no es accesible (404, Req 23.3).
     * @throws ReglaNegocioException        si algun monto es invalido (422).
     */
    @Transactional
    public PresupuestoDto actualizarPresupuesto(UUID presupuestoId,
                                                BigDecimal ingresosEstimados,
                                                BigDecimal egresosEstimados) {
        String actor = actorActual();
        Presupuesto presupuesto = cargar(presupuestoId, actor);
        String anterior = resumen(presupuesto);
        presupuesto.actualizar(ingresosEstimados, egresosEstimados, actor);
        Presupuesto guardado = presupuestoRepository.save(presupuesto);
        auditar(actor, "actualizar", guardado.getId(),
                "actualizado Presupuesto [area=" + guardado.getArea()
                        + ", periodo=" + guardado.getPeriodo() + "]",
                anterior, resumen(guardado));
        return PresupuestoDto.de(guardado);
    }

    /**
     * Consulta puntual de un Presupuesto del tenant (Req 23.3).
     *
     * @param presupuestoId identificador del Presupuesto.
     * @return el DTO del Presupuesto.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public PresupuestoDto consultar(UUID presupuestoId) {
        String actor = actorActual();
        return PresupuestoDto.de(cargar(presupuestoId, actor));
    }

    /**
     * Calcula la variacion de un Presupuesto frente al ejercicio real (Req 62.2,
     * 62.3, 62.6, 62.7; Property 36) como una agregacion de <strong>solo
     * lectura</strong>: obtiene el real via {@link RealEjercidoPort} sin modificar
     * ningun origen, y evalua ingresos (tipo INGRESO) y egresos (tipo EGRESO) con
     * {@link CalculoVariacionPresupuesto} aplicando el umbral configurable.
     *
     * @param presupuestoId identificador del Presupuesto a evaluar.
     * @return el DTO de variacion de solo lectura.
     * @throws RecursoNoEncontradoException si no es accesible (404, Req 23.3).
     */
    @Transactional(readOnly = true)
    public VariacionPresupuestoDto consultarVariacion(UUID presupuestoId) {
        String actor = actorActual();
        Presupuesto presupuesto = cargar(presupuestoId, actor);

        BigDecimal umbral = propiedades.umbralDesviacion();
        BigDecimal ingresosReales =
                normalizarReal(realEjercido.realIngresos(presupuesto.getArea(), presupuesto.getPeriodo()));
        BigDecimal egresosReales =
                normalizarReal(realEjercido.realEgresos(presupuesto.getArea(), presupuesto.getPeriodo()));

        ResultadoVariacion variacionIngresos = CalculoVariacionPresupuesto.calcular(
                presupuesto.getIngresosEstimados(), ingresosReales, TipoPresupuesto.INGRESO, umbral);
        ResultadoVariacion variacionEgresos = CalculoVariacionPresupuesto.calcular(
                presupuesto.getEgresosEstimados(), egresosReales, TipoPresupuesto.EGRESO, umbral);

        return VariacionPresupuestoDto.de(
                presupuesto.getId(), presupuesto.getArea(), presupuesto.getPeriodo(),
                presupuesto.getIngresosEstimados(), ingresosReales, variacionIngresos,
                presupuesto.getEgresosEstimados(), egresosReales, variacionEgresos);
    }

    /**
     * Listado paginado de Presupuestos del tenant con filtros opcionales por area y
     * periodo (Req 62.4). Un filtro nulo/blanco no restringe; sin coincidencias se
     * devuelve una pagina vacia con total 0.
     *
     * @param area     area a filtrar; {@code null}/blanco no filtra.
     * @param periodo  periodo a filtrar; {@code null}/blanco no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Presupuestos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<PresupuestoDto> listar(String area, String periodo, Pageable pageable) {
        String filtroArea = (area == null || area.isBlank()) ? null : area.trim();
        String filtroPeriodo = (periodo == null || periodo.isBlank()) ? null : periodo.trim();
        return presupuestoRepository.buscarConFiltros(filtroArea, filtroPeriodo, pageable)
                .map(PresupuestoDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Presupuesto cargar(UUID presupuestoId, String actor) {
        if (presupuestoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Presupuesto solicitado.");
        }
        return presupuestoRepository.findById(presupuestoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, presupuestoId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Presupuesto solicitado.");
                });
    }

    private static BigDecimal normalizarReal(BigDecimal real) {
        if (real == null) {
            return CalculoVariacionPresupuesto.normalizar(BigDecimal.ZERO);
        }
        return CalculoVariacionPresupuesto.normalizar(real);
    }

    private static String resumen(Presupuesto presupuesto) {
        return "{\"ingresosEstimados\":\"" + presupuesto.getIngresosEstimados()
                + "\",\"egresosEstimados\":\"" + presupuesto.getEgresosEstimados() + "\"}";
    }

    private void auditar(String actor, String accion, UUID presupuestoId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_PRESUPUESTO,
                detalle + " [id=" + presupuestoId + "]", valorAnterior, valorNuevo));
    }

    private void auditarAccesoCruzado(String actor, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", RECURSO_PRESUPUESTO,
                "intento de acceso a presupuesto no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
