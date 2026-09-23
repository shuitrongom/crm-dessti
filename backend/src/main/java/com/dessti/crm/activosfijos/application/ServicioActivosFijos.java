package com.dessti.crm.activosfijos.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.activosfijos.adapter.out.persistence.ActivoFijoRepository;
import com.dessti.crm.activosfijos.adapter.out.persistence.DepreciacionRepository;
import com.dessti.crm.activosfijos.domain.ActivoFijo;
import com.dessti.crm.activosfijos.domain.Depreciacion;
import com.dessti.crm.activosfijos.domain.EstadoActivoFijo;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de los {@link ActivoFijo} y
 * sus {@link Depreciacion Depreciaciones} de periodo (Req 44). Replica el patron de
 * {@code ServicioOrdenesFabricacion}/{@code ServicioContabilidad}.
 *
 * <h2>Operaciones (Req 44)</h2>
 * <ul>
 *   <li><strong>crearActivo (Req 44.1, 44.2):</strong> valida los datos
 *       obligatorios (costo &gt; 0, fecha de adquisicion, vida util &gt; 0, metodo,
 *       valor residual en {@code [0, costo]}) via la fabrica pura del dominio; si
 *       algo falta o es invalido rechaza con 422 sin persistir. Audita el alta.</li>
 *   <li><strong>depreciar (Req 44.3, 38.2):</strong> carga el Activo_Fijo (404),
 *       rechaza depreciar un bien dado de baja (422), impone una depreciacion por
 *       activo y periodo (409), calcula el monto del periodo (acotado por el
 *       remanente depreciable) y, si es &gt; 0, genera la Poliza_Contable
 *       correspondiente via {@link PolizaDepreciacionPort}. Persiste la Depreciacion
 *       enlazando la poliza, incrementa la acumulada del Activo_Fijo y audita.</li>
 *   <li><strong>darDeBaja (Req 44.4):</strong> transita el Activo_Fijo a
 *       {@code baja} conservando el historico; audita el estado anterior y el
 *       nuevo. 409 si ya estaba dado de baja.</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria del intento si no es
 *       accesible.</li>
 *   <li><strong>listar (Req 44.5):</strong> listado paginado (20/100) con filtro
 *       por estado.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 44.6)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). El alta, la depreciacion y la baja se auditan via
 * {@link AuditoriaPort} con el actor derivado del contexto.</p>
 */
@Service
public class ServicioActivosFijos {

    /** Tipo de recurso de auditoria/RBAC del Activo_Fijo. */
    static final String RECURSO_ACTIVO_FIJO = "activo_fijo";

    /** Tipo de recurso de auditoria/RBAC de la Depreciacion. */
    static final String RECURSO_DEPRECIACION = "depreciacion";

    private final ActivoFijoRepository activoFijoRepository;
    private final DepreciacionRepository depreciacionRepository;
    private final PolizaDepreciacionPort polizaDepreciacionPort;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioActivosFijos(ActivoFijoRepository activoFijoRepository,
                                DepreciacionRepository depreciacionRepository,
                                PolizaDepreciacionPort polizaDepreciacionPort,
                                AuditoriaPort auditoria,
                                Clock clock) {
        this.activoFijoRepository = activoFijoRepository;
        this.depreciacionRepository = depreciacionRepository;
        this.polizaDepreciacionPort = polizaDepreciacionPort;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Activo_Fijo (Req 44.1, 44.2, 44.4, 44.5)
    // ------------------------------------------------------------------

    /**
     * Da de alta un Activo_Fijo validando los datos obligatorios (Req 44.1, 44.2).
     * La fabrica pura del dominio rechaza con 422 los faltantes o invalidos sin
     * persistir. Audita el alta (Req 44.6).
     *
     * @param comando datos del Activo_Fijo a crear.
     * @return el DTO del Activo_Fijo creado.
     * @throws ReglaNegocioException si faltan datos o son invalidos (422).
     */
    @Transactional
    public ActivoFijoDto crearActivo(CrearActivoFijoCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Activo_Fijo son obligatorios.");
        }
        ActivoFijo activo = ActivoFijo.crear(
                comando.nombre(), comando.costo(), comando.fechaAdquisicion(),
                comando.vidaUtilMeses(), comando.metodo(), comando.valorResidual(), actor);
        ActivoFijo guardado = activoFijoRepository.save(activo);
        auditar(actor, "crear", RECURSO_ACTIVO_FIJO, guardado.getId(),
                "alta de Activo_Fijo [nombre=" + guardado.getNombre() + ", costo="
                        + guardado.getCosto().toPlainString() + ", metodo="
                        + guardado.getMetodoDepreciacion().valorBd() + "]", null, null);
        return ActivoFijoDto.de(guardado);
    }

    /**
     * Consulta puntual de un Activo_Fijo del tenant (Req 23.3).
     *
     * @param activoFijoId identificador del Activo_Fijo.
     * @return el DTO del Activo_Fijo.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ActivoFijoDto consultar(UUID activoFijoId) {
        String actor = actorActual();
        return ActivoFijoDto.de(cargar(activoFijoId, actor));
    }

    /**
     * Listado paginado de Activos_Fijos del tenant con filtro opcional por estado
     * (Req 44.5). Sin filtro devuelve todos; sin coincidencias, pagina vacia.
     *
     * @param estado   etiqueta de estado a filtrar ({@code activo}/{@code baja});
     *                 {@code null}/blanco no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Activos_Fijos como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<ActivoFijoDto> listar(String estado, Pageable pageable) {
        EstadoActivoFijo filtro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return activoFijoRepository.buscarConFiltros(filtro, pageable)
                .map(ActivoFijoDto::de);
    }

    /**
     * Da de baja (o registra la venta de) un Activo_Fijo, conservando el historico
     * (Req 44.4). Transita el estado a {@code baja} y audita el estado anterior y el
     * nuevo (Req 44.6).
     *
     * @param activoFijoId identificador del Activo_Fijo.
     * @return el DTO del Activo_Fijo en estado {@code baja}.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si ya
     *         estaba dado de baja (409).
     */
    @Transactional
    public ActivoFijoDto darDeBaja(UUID activoFijoId) {
        String actor = actorActual();
        ActivoFijo activo = cargar(activoFijoId, actor);
        String anterior = activo.getEstado().valorBd();
        activo.darDeBaja(actor);
        ActivoFijo guardado = activoFijoRepository.save(activo);
        auditar(actor, "cambiar_estado", RECURSO_ACTIVO_FIJO, guardado.getId(),
                "baja de Activo_Fijo '" + anterior + "' -> '"
                        + guardado.getEstado().valorBd() + "'",
                anterior, guardado.getEstado().valorBd());
        return ActivoFijoDto.de(guardado);
    }

    // ------------------------------------------------------------------
    // Depreciacion (Req 44.3, 38.2)
    // ------------------------------------------------------------------

    /**
     * Corre la depreciacion de un periodo para un Activo_Fijo (Req 44.3, 38.2).
     *
     * <p>Flujo: carga el Activo_Fijo (404); rechaza si esta dado de baja (422);
     * impone una depreciacion por activo y periodo pre-verificando el UNIQUE (409);
     * aplica la depreciacion del periodo en el dominio (monto acotado por el
     * remanente depreciable). Si el monto aplicado es cero (bien ya totalmente
     * depreciado) rechaza con 422 sin registrar nada. Si el monto es &gt; 0, genera
     * la Poliza_Contable correspondiente via {@link PolizaDepreciacionPort} (que
     * puede no generarla si el catalogo contable esta incompleto, en cuyo caso se
     * registra sin poliza y se audita), persiste la Depreciacion enlazando la
     * poliza, incrementa la acumulada del Activo_Fijo y audita.</p>
     *
     * @param activoFijoId identificador del Activo_Fijo a depreciar.
     * @param periodo      periodo mensual {@code 'AAAA-MM'}; obligatorio.
     * @return el DTO de la Depreciacion registrada.
     * @throws RecursoNoEncontradoException si el Activo_Fijo no es accesible (404).
     * @throws ReglaNegocioException si el periodo es invalido, el bien esta dado de
     *         baja o ya esta totalmente depreciado (422).
     * @throws ConflictoUnicidadException si ya existe la depreciacion de ese activo
     *         y periodo (409).
     */
    @Transactional
    public DepreciacionDto depreciar(UUID activoFijoId, String periodo) {
        String actor = actorActual();
        if (periodo == null || periodo.isBlank()) {
            throw new ReglaNegocioException("El periodo a depreciar es obligatorio.");
        }
        String periodoNorm = periodo.strip();

        ActivoFijo activo = cargar(activoFijoId, actor);

        // Regla: no se deprecia un Activo_Fijo dado de baja (Req 44.3/44.4).
        if (!activo.estaActivo()) {
            throw new ReglaNegocioException(
                    "No se puede depreciar un Activo_Fijo dado de baja.");
        }

        // Una depreciacion por activo y periodo (Req 44.3): pre-check del UNIQUE.
        if (depreciacionRepository.existsByActivoFijoIdAndPeriodo(activoFijoId, periodoNorm)) {
            throw new ConflictoUnicidadException(
                    "el Activo_Fijo ya tiene una depreciacion registrada para el periodo "
                            + periodoNorm);
        }

        // Aplica la depreciacion del periodo (clamp por el remanente depreciable).
        BigDecimal monto = activo.aplicarDepreciacion(actor);
        if (monto.signum() <= 0) {
            throw new ReglaNegocioException(
                    "el Activo_Fijo ya esta totalmente depreciado; no hay depreciacion para el periodo "
                            + periodoNorm);
        }

        // Genera la Poliza_Contable de la depreciacion (Req 44.3/38.2); opcional
        // segun disponibilidad del catalogo contable (ver PolizaDepreciacionPort).
        LocalDate fecha = LocalDate.now(clock);
        Optional<UUID> polizaId = polizaDepreciacionPort.generarPolizaDepreciacion(
                fecha, activoFijoId, periodoNorm, monto, actor);
        if (polizaId.isEmpty()) {
            auditar(actor, "poliza_omitida", RECURSO_DEPRECIACION, activoFijoId,
                    "depreciacion del periodo " + periodoNorm
                            + " registrada sin Poliza_Contable (catalogo contable incompleto)",
                    null, null);
        }

        Depreciacion depreciacion = Depreciacion.registrar(
                activoFijoId, periodoNorm, monto, activo.getDepreciacionAcumulada(),
                polizaId.orElse(null), actor);

        Depreciacion guardada;
        try {
            // saveAndFlush para materializar la violacion del UNIQUE
            // (tenant_id, activo_fijo_id, periodo) dentro de este try (segunda capa
            // de defensa ante concurrencia).
            guardada = depreciacionRepository.saveAndFlush(depreciacion);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoUnicidadException(
                    "el Activo_Fijo ya tiene una depreciacion registrada para el periodo "
                            + periodoNorm);
        }
        activoFijoRepository.save(activo);

        auditar(actor, "depreciar", RECURSO_DEPRECIACION, guardada.getId(),
                "depreciacion del periodo " + periodoNorm + " [activo_fijo=" + activoFijoId
                        + ", monto=" + guardada.getMonto().toPlainString() + ", acumulada="
                        + guardada.getDepreciacionAcumuladaResultante().toPlainString()
                        + ", poliza=" + guardada.getPolizaContableId() + "]", null, null);
        return DepreciacionDto.de(guardada);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private ActivoFijo cargar(UUID activoFijoId, String actor) {
        if (activoFijoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Activo_Fijo solicitado.");
        }
        return activoFijoRepository.findById(activoFijoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_ACTIVO_FIJO, activoFijoId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Activo_Fijo solicitado.");
                });
    }

    private EstadoActivoFijo interpretarEstado(String etiqueta) {
        try {
            return EstadoActivoFijo.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Activo_Fijo desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, String recurso, UUID recursoId,
                         String detalle, String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, recurso,
                detalle + " [id=" + recursoId + "]", valorAnterior, valorNuevo));
    }

    private void auditarAccesoCruzado(String actor, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", recurso,
                "intento de acceso a " + recurso + " no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
