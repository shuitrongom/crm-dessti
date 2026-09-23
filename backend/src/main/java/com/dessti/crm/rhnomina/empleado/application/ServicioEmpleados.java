package com.dessti.crm.rhnomina.empleado.application;

import java.util.Locale;
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
import com.dessti.crm.rhnomina.empleado.adapter.out.persistence.ContratoLaboralRepository;
import com.dessti.crm.rhnomina.empleado.adapter.out.persistence.EmpleadoRepository;
import com.dessti.crm.rhnomina.empleado.adapter.out.persistence.IncidenciaRepository;
import com.dessti.crm.rhnomina.empleado.domain.ContratoLaboral;
import com.dessti.crm.rhnomina.empleado.domain.Empleado;
import com.dessti.crm.rhnomina.empleado.domain.Incidencia;
import com.dessti.crm.rhnomina.empleado.domain.Periodicidad;
import com.dessti.crm.rhnomina.empleado.domain.TipoContrato;
import com.dessti.crm.rhnomina.empleado.domain.TipoIncidencia;

/**
 * Servicio de aplicacion del modulo base de RH/nomina que gobierna el ciclo de
 * vida del {@link Empleado}, su {@link ContratoLaboral} y sus {@link Incidencia}
 * (Req 40). Replica el patron establecido por {@code ServicioClientes} del modulo
 * comercial-crm (borrado logico, auditoria, aislamiento multi-tenant en dos capas).
 *
 * <h2>Operaciones (Req 40)</h2>
 * <ul>
 *   <li><strong>altaEmpleado (Req 40.1, 40.2):</strong> valida los datos
 *       obligatorios del Empleado (RFC/CURP/NSS incluidos) y crea atomicamente el
 *       Empleado y su primer Contrato_Laboral; rechaza el RFC duplicado entre
 *       Empleados activos del tenant (409); audita ambas altas.</li>
 *   <li><strong>registrarIncidencia (Req 40.3):</strong> verifica que el Empleado
 *       exista y este activo (404); persiste la Incidencia vinculada al Empleado y
 *       al Periodo_Nomina; audita.</li>
 *   <li><strong>darDeBaja (Req 40.4):</strong> borrado logico ({@code activo=false})
 *       conservando el historico de Contrato_Laboral e Incidencia; audita.</li>
 *   <li><strong>consultarEmpleado (Req 4.3, 23.3):</strong> consulta puntual; 404 +
 *       auditoria del intento si el Empleado no existe o pertenece a otro tenant.</li>
 *   <li><strong>listarEmpleados (Req 40.5, 40.6):</strong> listado paginado
 *       (20/100) filtrable por nombre (contiene, sin distinguir mayusculas) y por
 *       estado (activo/inactivo).</li>
 *   <li><strong>listarContratos / listarIncidencias:</strong> historico por
 *       Empleado (Req 40.4).</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant y auditoria (Req 23, 40.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4) y {@code TenantScopedEntity} lo asigna al persistir. Cada
 * operacion se registra via {@link AuditoriaPort} como evento de tenant
 * ({@link EventoAuditoria#deTenant}) con el actor derivado del contexto de
 * seguridad. Un acceso a un recurso de otro tenant devuelve 404 y se audita como
 * intento de acceso cruzado (Req 4.3, 23.3).</p>
 */
@Service
public class ServicioEmpleados {

    /** Tipo de recurso de auditoria/RBAC del Empleado. */
    static final String RECURSO_EMPLEADO = "empleado";

    /** Tipo de recurso de auditoria/RBAC del Contrato_Laboral. */
    static final String RECURSO_CONTRATO = "contrato_laboral";

    /** Tipo de recurso de auditoria/RBAC de la Incidencia. */
    static final String RECURSO_INCIDENCIA = "incidencia";

    private final EmpleadoRepository empleadoRepository;
    private final ContratoLaboralRepository contratoRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final AuditoriaPort auditoria;

    public ServicioEmpleados(EmpleadoRepository empleadoRepository,
                             ContratoLaboralRepository contratoRepository,
                             IncidenciaRepository incidenciaRepository,
                             AuditoriaPort auditoria) {
        this.empleadoRepository = empleadoRepository;
        this.contratoRepository = contratoRepository;
        this.incidenciaRepository = incidenciaRepository;
        this.auditoria = auditoria;
    }

    /**
     * Da de alta un Empleado con sus datos obligatorios validados (Req 40.1,
     * 40.2) y su primer Contrato_Laboral, de forma atomica. El RFC debe ser unico
     * entre los Empleados activos del tenant (409).
     *
     * @param comando datos del Empleado y de su Contrato_Laboral.
     * @return el DTO del Empleado creado.
     * @throws ReglaNegocioException      si faltan o son invalidos los datos
     *                                    obligatorios (422, Req 40.2).
     * @throws ConflictoUnicidadException si ya existe un Empleado activo con el
     *                                    mismo RFC en el tenant (409).
     */
    @Transactional
    public EmpleadoDto altaEmpleado(AltaEmpleadoCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Empleado son obligatorios.");
        }

        // Construye y valida el Empleado y el Contrato_Laboral (Req 40.1, 40.2)
        // antes de la comprobacion de unicidad, para que un dato invalido produzca
        // 422 y no 409.
        Empleado empleado = Empleado.crear(
                comando.nombre(), comando.rfc(), comando.curp(), comando.nss(),
                comando.fechaIngreso(), actor);
        TipoContrato tipoContrato = resolverTipoContrato(comando.tipoContrato());
        Periodicidad periodicidad = resolverPeriodicidad(comando.periodicidad());
        ContratoLaboral contrato = ContratoLaboral.crear(
                empleado.getId(), tipoContrato, comando.salarioDiario(), periodicidad,
                comando.fechaInicio(), actor);

        // Pre-comprobacion de unicidad de RFC entre activos del tenant.
        if (empleadoRepository.existsByRfcAndActivoTrue(empleado.getRfc())) {
            throw new ConflictoUnicidadException(
                    "Ya existe un Empleado activo con el RFC '" + empleado.getRfc() + "'.");
        }

        Empleado empleadoGuardado = guardarEmpleadoTraduciendoUnicidad(empleado);
        ContratoLaboral contratoGuardado = contratoRepository.save(contrato);

        auditarEmpleado(actor, "crear", empleadoGuardado.getId(),
                "alta de empleado '" + empleadoGuardado.getNombre()
                        + "' (rfc=" + empleadoGuardado.getRfc() + ")");
        auditarContrato(actor, "crear", contratoGuardado.getId(),
                "alta de contrato_laboral " + contratoGuardado.getTipo().valorBd()
                        + " del empleado " + empleadoGuardado.getId());
        return EmpleadoDto.de(empleadoGuardado);
    }

    /**
     * Registra una Incidencia para un Empleado activo del tenant en un
     * Periodo_Nomina (Req 40.3) y audita la operacion.
     *
     * @param comando datos de la Incidencia.
     * @return el DTO de la Incidencia creada.
     * @throws RecursoNoEncontradoException si el Empleado no existe, esta inactivo
     *                                      o pertenece a otro tenant (404).
     * @throws ReglaNegocioException        si los datos de la Incidencia son
     *                                      invalidos (422).
     */
    @Transactional
    public IncidenciaDto registrarIncidencia(RegistrarIncidenciaCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.empleadoId() == null) {
            throw new ReglaNegocioException("El Empleado de la Incidencia es obligatorio.");
        }
        Empleado empleado = cargarEmpleadoActivo(comando.empleadoId(), actor);

        TipoIncidencia tipo = resolverTipoIncidencia(comando.tipo());
        Incidencia incidencia = Incidencia.registrar(
                empleado.getId(), comando.periodoNomina(), tipo,
                comando.cantidad(), comando.descripcion(), actor);
        Incidencia guardada = incidenciaRepository.save(incidencia);

        auditarIncidencia(actor, "crear", guardada.getId(),
                "registrada incidencia " + guardada.getTipo().valorBd() + " del empleado "
                        + empleado.getId() + " en el periodo " + guardada.getPeriodoNomina());
        return IncidenciaDto.de(guardada);
    }

    /**
     * Realiza el borrado logico de un Empleado activo del tenant (Req 40.4):
     * marca {@code activo=false} conservando el historico de su Contrato_Laboral e
     * Incidencia, y audita.
     *
     * @param empleadoId identificador del Empleado.
     * @return el DTO del Empleado dado de baja.
     * @throws RecursoNoEncontradoException si el Empleado no existe, ya esta
     *                                      inactivo o pertenece a otro tenant (404).
     */
    @Transactional
    public EmpleadoDto darDeBaja(UUID empleadoId) {
        String actor = actorActual();
        Empleado empleado = cargarEmpleadoActivo(empleadoId, actor);
        empleado.desactivar(actor);
        Empleado guardado = empleadoRepository.save(empleado);
        auditarEmpleado(actor, "eliminar", guardado.getId(),
                "baja logica del empleado '" + guardado.getNombre()
                        + "' (rfc=" + guardado.getRfc() + ")");
        return EmpleadoDto.de(guardado);
    }

    /**
     * Consulta puntual de un Empleado activo del tenant (Req 4.3, 23.3). Un
     * Empleado inexistente, inactivo o de otro tenant produce 404 y se audita el
     * intento como acceso cruzado.
     *
     * @param empleadoId identificador del Empleado.
     * @return el DTO del Empleado.
     * @throws RecursoNoEncontradoException si el Empleado no es accesible (404).
     */
    @Transactional(readOnly = true)
    public EmpleadoDto consultarEmpleado(UUID empleadoId) {
        String actor = actorActual();
        Empleado empleado = cargarEmpleadoActivo(empleadoId, actor);
        return EmpleadoDto.de(empleado);
    }

    /**
     * Listado paginado de Empleados del tenant, filtrable por nombre (contiene,
     * sin distinguir mayusculas) y por estado (Req 40.5, 40.6). Un nombre nulo o
     * en blanco no filtra por nombre; un {@code activo} nulo no filtra por estado.
     *
     * @param nombre   subcadena a buscar en el nombre; {@code null}/blanco no filtra.
     * @param activo   estado a filtrar; {@code null} no filtra por estado.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Empleados como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<EmpleadoDto> listarEmpleados(String nombre, Boolean activo, Pageable pageable) {
        String criterio = (nombre == null) ? "" : nombre.strip().toLowerCase(Locale.ROOT);
        return empleadoRepository.buscarConFiltros(criterio, activo, pageable)
                .map(EmpleadoDto::de);
    }

    /**
     * Lista el historico de Contrato_Laboral de un Empleado accesible del tenant
     * (Req 40.4), de forma paginada.
     *
     * @param empleadoId identificador del Empleado.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de Contrato_Laboral como DTOs.
     * @throws RecursoNoEncontradoException si el Empleado no es accesible (404).
     */
    @Transactional(readOnly = true)
    public Page<ContratoLaboralDto> listarContratos(UUID empleadoId, Pageable pageable) {
        String actor = actorActual();
        Empleado empleado = cargarEmpleadoActivo(empleadoId, actor);
        return contratoRepository.findByEmpleadoId(empleado.getId(), pageable)
                .map(ContratoLaboralDto::de);
    }

    /**
     * Lista el historico de Incidencia de un Empleado accesible del tenant
     * (Req 40.3, 40.4), de forma paginada.
     *
     * @param empleadoId identificador del Empleado.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de Incidencia como DTOs.
     * @throws RecursoNoEncontradoException si el Empleado no es accesible (404).
     */
    @Transactional(readOnly = true)
    public Page<IncidenciaDto> listarIncidencias(UUID empleadoId, Pageable pageable) {
        String actor = actorActual();
        Empleado empleado = cargarEmpleadoActivo(empleadoId, actor);
        return incidenciaRepository.findByEmpleadoId(empleado.getId(), pageable)
                .map(IncidenciaDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Carga un Empleado activo por id dentro del tenant vigente; si no es
     * accesible (inexistente, inactivo o de otro tenant) audita el intento y
     * lanza 404 (Req 4.3, 23.3).
     */
    private Empleado cargarEmpleadoActivo(UUID empleadoId, String actor) {
        if (empleadoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Empleado solicitado.");
        }
        return empleadoRepository.findByIdAndActivoTrue(empleadoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_EMPLEADO, empleadoId);
                    throw new RecursoNoEncontradoException("No se encontro el Empleado solicitado.");
                });
    }

    private TipoContrato resolverTipoContrato(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El tipo del Contrato_Laboral es obligatorio.");
        }
        try {
            return TipoContrato.desdeValorBd(valor.strip().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("El tipo del Contrato_Laboral es invalido.");
        }
    }

    private Periodicidad resolverPeriodicidad(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La periodicidad del Contrato_Laboral es obligatoria.");
        }
        try {
            return Periodicidad.desdeValorBd(valor.strip().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("La periodicidad del Contrato_Laboral es invalida.");
        }
    }

    private TipoIncidencia resolverTipoIncidencia(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El tipo de la Incidencia es obligatorio.");
        }
        try {
            return TipoIncidencia.desdeValorBd(valor.strip().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("El tipo de la Incidencia es invalido.");
        }
    }

    private Empleado guardarEmpleadoTraduciendoUnicidad(Empleado empleado) {
        try {
            return empleadoRepository.saveAndFlush(empleado);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente contra el indice parcial uq_empleado_rfc_activo_por_tenant (V32).
            throw new ConflictoUnicidadException(
                    "Ya existe un Empleado activo con el RFC '" + empleado.getRfc() + "'.");
        }
    }

    private void auditarEmpleado(String actor, String accion, UUID empleadoId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_EMPLEADO,
                detalle + " [id=" + empleadoId + "]", null, null));
    }

    private void auditarContrato(String actor, String accion, UUID contratoId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_CONTRATO,
                detalle + " [id=" + contratoId + "]", null, null));
    }

    private void auditarIncidencia(String actor, String accion, UUID incidenciaId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_INCIDENCIA,
                detalle + " [id=" + incidenciaId + "]", null, null));
    }

    /**
     * Audita un intento de acceso a un recurso no accesible (inexistente o de
     * otro tenant), antes de responder 404 (Req 4.3, 23.3).
     */
    private void auditarAccesoCruzado(String actor, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", recurso,
                "intento de acceso a " + recurso + " no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    /**
     * Resuelve el identificador del actor autenticado para la auditoria; si no
     * hay contexto de seguridad, usa "sistema".
     */
    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
