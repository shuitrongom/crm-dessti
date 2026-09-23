package com.dessti.crm.estrategia.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.estrategia.adapter.out.persistence.EsenciaEmpresaRepository;
import com.dessti.crm.estrategia.adapter.out.persistence.HistorialAvanceObjetivoRepository;
import com.dessti.crm.estrategia.adapter.out.persistence.ObjetivoEstrategicoRepository;
import com.dessti.crm.estrategia.adapter.out.persistence.ResultadoClaveRepository;
import com.dessti.crm.estrategia.domain.EsenciaEmpresa;
import com.dessti.crm.estrategia.domain.HistorialAvanceObjetivo;
import com.dessti.crm.estrategia.domain.ObjetivoEstrategico;
import com.dessti.crm.estrategia.domain.ResultadoClave;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Servicio de aplicacion que gobierna la planeacion estrategica: la
 * {@link EsenciaEmpresa} (mision/vision/valores) y los {@link ObjetivoEstrategico}
 * con sus {@link ResultadoClave} ponderados e historial de avance (Req 58). Replica
 * el patron establecido por {@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones (Req 58)</h2>
 * <ul>
 *   <li><strong>guardarEsencia (Req 58.1):</strong> registra o actualiza (upsert) la
 *       mision, vision y valores del tenant; audita (Req 58.7).</li>
 *   <li><strong>consultarEsencia (Req 58.1):</strong> devuelve la esencia del tenant
 *       (404 si aun no existe).</li>
 *   <li><strong>crearObjetivo (Req 58.2, 58.3):</strong> valida los campos
 *       obligatorios (422 nombrando el faltante), persiste con avance inicial 0 y
 *       registra el primer punto del historial; audita.</li>
 *   <li><strong>agregarResultadoClave (Req 58.8):</strong> asocia un resultado clave
 *       ponderado y recalcula el avance del objetivo (agregacion de solo lectura);
 *       registra el historial y audita.</li>
 *   <li><strong>actualizarValorResultadoClave (Req 58.8):</strong> actualiza el valor
 *       actual de una metrica, recalcula el avance del objetivo, registra el
 *       historial y audita.</li>
 *   <li><strong>actualizarAvanceManual (Req 58.4):</strong> fija el avance de un
 *       objetivo <em>sin</em> resultados clave (acotado a [0, 100]), registra el
 *       historial y audita. Con resultados clave el avance es derivado (422).</li>
 *   <li><strong>consultarObjetivo (Req 58.10):</strong> devuelve el objetivo con su
 *       avance y estado derivado (en_riesgo/en_curso/cumplido) a la fecha actual
 *       (404 si no es accesible, con auditoria del intento).</li>
 *   <li><strong>listarObjetivos (Req 58.5, 58.6):</strong> listado paginado (20/100)
 *       con filtros por periodo (vigencia en una fecha) y por responsable.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 58.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). Cada operacion relevante se registra via {@link AuditoriaPort} con el
 * actor derivado del contexto. El estado derivado y el historial usan el
 * {@link Clock} inyectado para el instante/fecha, de modo determinista en pruebas.</p>
 *
 * <p><strong>Fijado explicito del tenant en la transaccion (fix del 404):</strong>
 * al inicio de CADA operacion transaccional se invoca
 * {@link TenantSessionInitializer#applyTenant(java.util.UUID)} con
 * {@link TenantContext#require()}, replicando el patron probado de
 * {@code ServicioEmpresas}/{@code ServicioSuscripciones}/{@code ServicioFacturacionRenta}.
 * Esto garantiza que {@code app.current_tenant} (RLS, Capa 2) quede fijada sobre la
 * MISMA conexion de la transaccion del servicio, sin depender de que el aspecto
 * {@code TenantRlsAspect} o el filtro web hayan podido fijarla. Antes, con
 * {@code open-in-view=false}, ni el filtro de Hibernate (Capa 1, habilitado en el
 * filtro web sobre una sesion efimera) ni {@code app.current_tenant} (Capa 2) se
 * aplicaban de forma fiable sobre la sesion transaccional de lectura, por lo que la
 * RLS ocultaba la fila propia del tenant y {@code consultarEsencia} devolvia 404 aun
 * existiendo la esencia. Ademas, la lectura de la esencia filtra el tenant de forma
 * EXPLICITA en la consulta ({@code findFirstByTenantIdOrderByCreatedAtAsc}).</p>
 */
@Service
public class ServicioEstrategia {

    /** Tipo de recurso de auditoria/RBAC de la esencia (mision/vision/valores). */
    static final String RECURSO_ESENCIA = "planeacion_estrategica";

    /** Tipo de recurso de auditoria/RBAC del Objetivo_Estrategico. */
    static final String RECURSO_OBJETIVO = "objetivo_estrategico";

    private final EsenciaEmpresaRepository esenciaRepository;
    private final ObjetivoEstrategicoRepository objetivoRepository;
    private final ResultadoClaveRepository resultadoClaveRepository;
    private final HistorialAvanceObjetivoRepository historialRepository;
    private final AuditoriaPort auditoria;
    private final Clock clock;
    private final TenantSessionInitializer tenantSession;

    public ServicioEstrategia(EsenciaEmpresaRepository esenciaRepository,
                              ObjetivoEstrategicoRepository objetivoRepository,
                              ResultadoClaveRepository resultadoClaveRepository,
                              HistorialAvanceObjetivoRepository historialRepository,
                              AuditoriaPort auditoria,
                              Clock clock,
                              TenantSessionInitializer tenantSession) {
        this.esenciaRepository = esenciaRepository;
        this.objetivoRepository = objetivoRepository;
        this.resultadoClaveRepository = resultadoClaveRepository;
        this.historialRepository = historialRepository;
        this.auditoria = auditoria;
        this.clock = clock;
        this.tenantSession = tenantSession;
    }

    /**
     * Fija {@code app.current_tenant} (RLS, Capa 2) sobre la conexion de la
     * transaccion en curso con el tenant del contexto autenticado (Req 23.4), antes de
     * leer o escribir filas tenant-scoped. Devuelve el {@code tenant_id} vigente para
     * las consultas tenant-explicitas. Debe invocarse al inicio de cada metodo
     * {@code @Transactional} de este servicio.
     *
     * @return el {@code tenant_id} vigente derivado del {@link TenantContext}.
     */
    private java.util.UUID fijarTenantVigente() {
        java.util.UUID tenantId = TenantContext.require();
        tenantSession.applyTenant(tenantId);
        return tenantId;
    }

    // ------------------------------------------------------------------
    // Esencia (mision/vision/valores) â€” Req 58.1
    // ------------------------------------------------------------------

    /**
     * Registra o actualiza (upsert) la mision, vision y valores del tenant (Req 58.1).
     * Existe a lo sumo una esencia por tenant; si ya existe se actualiza, si no se
     * crea. Audita la operacion (Req 58.7).
     *
     * @param mision  mision; opcional.
     * @param vision  vision; opcional.
     * @param valores valores; opcional.
     * @return el DTO de la esencia resultante.
     */
    @Transactional
    public EsenciaEmpresaDto guardarEsencia(String mision, String vision, String valores) {
        UUID tenantId = fijarTenantVigente();
        String actor = actorActual();
        EsenciaEmpresa esencia = esenciaRepository
                .findFirstByTenantIdOrderByCreatedAtAsc(tenantId).orElse(null);
        String accion;
        if (esencia == null) {
            esencia = EsenciaEmpresa.crear(mision, vision, valores, actor);
            accion = "crear_esencia";
        } else {
            esencia.actualizar(mision, vision, valores, actor);
            accion = "actualizar_esencia";
        }
        EsenciaEmpresa guardada = esenciaRepository.save(esencia);
        auditar(actor, accion, RECURSO_ESENCIA, guardada.getId(),
                "registrada mision/vision/valores de la Empresa");
        return EsenciaEmpresaDto.de(guardada);
    }

    /**
     * Consulta la esencia (mision/vision/valores) del tenant (Req 58.1).
     *
     * @return el DTO de la esencia.
     * @throws RecursoNoEncontradoException si aun no se ha registrado (404).
     */
    @Transactional(readOnly = true)
    public EsenciaEmpresaDto consultarEsencia() {
        UUID tenantId = fijarTenantVigente();
        EsenciaEmpresa esencia = esenciaRepository
                .findFirstByTenantIdOrderByCreatedAtAsc(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La Empresa aun no ha registrado su mision, vision y valores."));
        return EsenciaEmpresaDto.de(esencia);
    }

    // ------------------------------------------------------------------
    // Objetivos â€” Req 58.2..58.10
    // ------------------------------------------------------------------

    /**
     * Crea un Objetivo_Estrategico validando los campos obligatorios (Req 58.2,
     * 58.3), con avance inicial 0, registra el primer punto del historial y audita
     * (Req 58.7).
     *
     * @param command datos del objetivo a crear; obligatorio.
     * @return el DTO del objetivo creado, con su avance (0) y estado derivado.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si falta un campo
     *         obligatorio (422, nombrando el campo, Req 58.3).
     */
    @Transactional
    public ObjetivoEstrategicoDto crearObjetivo(CrearObjetivoCommand command) {
        fijarTenantVigente();
        String actor = actorActual();
        ObjetivoEstrategico objetivo = ObjetivoEstrategico.crear(
                command.nombre(), command.responsable(), command.periodoInicio(),
                command.periodoFin(), command.meta(), actor);
        ObjetivoEstrategico guardado = objetivoRepository.save(objetivo);
        registrarHistorial(guardado, actor);
        auditar(actor, "crear", RECURSO_OBJETIVO, guardado.getId(),
                "creado Objetivo_Estrategico '" + guardado.getNombre()
                        + "' [responsable=" + guardado.getResponsable() + "]");
        return proyectar(guardado);
    }

    /**
     * Agrega un resultado clave ponderado a un objetivo y recalcula su avance como el
     * porcentaje ponderado de sus resultados clave (Req 58.8). Registra el historial
     * y audita.
     *
     * @param objetivoId identificador del objetivo.
     * @param command    datos del resultado clave; obligatorio.
     * @return el DTO del objetivo con su avance recalculado.
     * @throws RecursoNoEncontradoException si el objetivo no es accesible (404).
     */
    @Transactional
    public ObjetivoEstrategicoDto agregarResultadoClave(UUID objetivoId,
                                                        AgregarResultadoClaveCommand command) {
        fijarTenantVigente();
        String actor = actorActual();
        ObjetivoEstrategico objetivo = cargarObjetivo(objetivoId, actor);
        ResultadoClave resultado = ResultadoClave.crear(
                command.descripcion(), command.valorObjetivo(), command.valorActual(),
                command.peso(), actor);
        objetivo.agregarResultadoClave(resultado, actor);
        ObjetivoEstrategico guardado = objetivoRepository.save(objetivo);
        registrarHistorial(guardado, actor);
        auditar(actor, "agregar_resultado_clave", RECURSO_OBJETIVO, guardado.getId(),
                "agregado resultado clave; avance recalculado a "
                        + guardado.getAvance().toPlainString() + "%");
        return proyectar(guardado);
    }

    /**
     * Actualiza el valor actual de un resultado clave y recalcula el avance del
     * objetivo contenedor (Req 58.8). Registra el historial y audita.
     *
     * @param resultadoClaveId identificador del resultado clave.
     * @param nuevoValorActual nuevo valor actual medido; no negativo (Req 58.8).
     * @return el DTO del objetivo con su avance recalculado.
     * @throws RecursoNoEncontradoException si el resultado clave no es accesible (404).
     */
    @Transactional
    public ObjetivoEstrategicoDto actualizarValorResultadoClave(UUID resultadoClaveId,
                                                                java.math.BigDecimal nuevoValorActual) {
        fijarTenantVigente();
        String actor = actorActual();
        ResultadoClave resultado = resultadoClaveRepository.findById(resultadoClaveId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_OBJETIVO, resultadoClaveId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el resultado clave solicitado.");
                });
        resultado.actualizarValorActual(nuevoValorActual, actor);
        ObjetivoEstrategico objetivo = resultado.getObjetivo();
        objetivo.recalcularAvancePorResultados(actor);
        ObjetivoEstrategico guardado = objetivoRepository.save(objetivo);
        registrarHistorial(guardado, actor);
        auditar(actor, "actualizar_resultado_clave", RECURSO_OBJETIVO, guardado.getId(),
                "actualizado resultado clave [id=" + resultadoClaveId
                        + "]; avance recalculado a " + guardado.getAvance().toPlainString() + "%");
        return proyectar(guardado);
    }

    /**
     * Fija el avance de un objetivo <em>sin</em> resultados clave, acotado a [0, 100]
     * (Req 58.9), conservando el historial (Req 58.4). Audita.
     *
     * @param objetivoId  identificador del objetivo.
     * @param nuevoAvance nuevo avance; se acota a [0, 100] (Req 58.9).
     * @return el DTO del objetivo con su avance actualizado.
     * @throws RecursoNoEncontradoException si el objetivo no es accesible (404).
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si el objetivo
     *         tiene resultados clave (422): en ese caso el avance es derivado.
     */
    @Transactional
    public ObjetivoEstrategicoDto actualizarAvanceManual(UUID objetivoId,
                                                         java.math.BigDecimal nuevoAvance) {
        fijarTenantVigente();
        String actor = actorActual();
        ObjetivoEstrategico objetivo = cargarObjetivo(objetivoId, actor);
        objetivo.actualizarAvanceManual(nuevoAvance, actor);
        ObjetivoEstrategico guardado = objetivoRepository.save(objetivo);
        registrarHistorial(guardado, actor);
        auditar(actor, "actualizar_avance", RECURSO_OBJETIVO, guardado.getId(),
                "avance actualizado manualmente a " + guardado.getAvance().toPlainString() + "%");
        return proyectar(guardado);
    }

    /**
     * Consulta un objetivo con su avance y estado derivado a la fecha actual
     * (Req 58.10).
     *
     * @param objetivoId identificador del objetivo.
     * @return el DTO del objetivo.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ObjetivoEstrategicoDto consultarObjetivo(UUID objetivoId) {
        fijarTenantVigente();
        String actor = actorActual();
        return proyectar(cargarObjetivo(objetivoId, actor));
    }

    /**
     * Listado paginado de Objetivos_Estrategicos del tenant con filtros opcionales por
     * periodo (vigencia en una fecha) y por responsable (Req 58.5, 58.6). El estado
     * derivado de cada objetivo se calcula a la fecha actual (Req 58.10).
     *
     * @param enPeriodo   fecha para filtrar objetivos vigentes; {@code null} no filtra.
     * @param responsable responsable a filtrar; {@code null}/blanco no filtra.
     * @param pageable    parametros de paginacion ya acotados (20/100).
     * @return la pagina de objetivos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ObjetivoEstrategicoDto> listarObjetivos(LocalDate enPeriodo, String responsable,
                                                        Pageable pageable) {
        fijarTenantVigente();
        String filtroResponsable = (responsable == null || responsable.isBlank())
                ? null : responsable.strip();
        LocalDate hoy = LocalDate.now(clock);
        return objetivoRepository.buscarConFiltros(enPeriodo, filtroResponsable, pageable)
                .map(objetivo -> ObjetivoEstrategicoDto.de(objetivo, hoy));
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private ObjetivoEstrategico cargarObjetivo(UUID objetivoId, String actor) {
        if (objetivoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Objetivo_Estrategico solicitado.");
        }
        return objetivoRepository.findById(objetivoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_OBJETIVO, objetivoId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Objetivo_Estrategico solicitado.");
                });
    }

    private ObjetivoEstrategicoDto proyectar(ObjetivoEstrategico objetivo) {
        return ObjetivoEstrategicoDto.de(objetivo, LocalDate.now(clock));
    }

    private void registrarHistorial(ObjetivoEstrategico objetivo, String actor) {
        historialRepository.save(HistorialAvanceObjetivo.registrar(
                objetivo.getId(), objetivo.getAvance(), Instant.now(clock), actor));
    }

    private void auditar(String actor, String accion, String recurso, UUID recursoId,
                         String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, recurso,
                detalle + " [id=" + recursoId + "]", null, null));
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
