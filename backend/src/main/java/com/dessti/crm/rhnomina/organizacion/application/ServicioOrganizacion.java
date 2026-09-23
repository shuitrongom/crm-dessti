package com.dessti.crm.rhnomina.organizacion.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.rhnomina.empleado.adapter.out.persistence.EmpleadoRepository;
import com.dessti.crm.rhnomina.empleado.domain.Empleado;
import com.dessti.crm.rhnomina.organizacion.adapter.out.persistence.AsignacionPuestoRepository;
import com.dessti.crm.rhnomina.organizacion.adapter.out.persistence.EvaluacionDesempenoRepository;
import com.dessti.crm.rhnomina.organizacion.adapter.out.persistence.PuestoRepository;
import com.dessti.crm.rhnomina.organizacion.domain.AsignacionPuesto;
import com.dessti.crm.rhnomina.organizacion.domain.EvaluacionDesempeno;
import com.dessti.crm.rhnomina.organizacion.domain.GrafoOrganigrama;
import com.dessti.crm.rhnomina.organizacion.domain.OrganigramaNodo;
import com.dessti.crm.rhnomina.organizacion.domain.Puesto;
import com.dessti.crm.rhnomina.organizacion.domain.PuestoJerarquia;

/**
 * Servicio de aplicacion del submodulo de <strong>organizacion de personal</strong>
 * del modulo rhnomina (Req 61). Gobierna los Puestos y su jerarquia (organigrama),
 * la asignacion de Empleados a Puestos y las Evaluacion_Desempeno, replicando el
 * patron de {@code ServicioEmpleados} (borrado logico, auditoria, aislamiento
 * multi-tenant en dos capas y 404 con auditoria de acceso cruzado).
 *
 * <h2>Operaciones (Req 61)</h2>
 * <ul>
 *   <li><strong>crearPuesto (Req 61.1, 61.7):</strong> crea un Puesto; si se indica
 *       superior, valida que exista (404) y que la arista no introduzca un ciclo en
 *       la jerarquia (422 via {@link GrafoOrganigrama}). Audita.</li>
 *   <li><strong>moverPuesto (Req 61.1, 61.7):</strong> cambia el superior de un
 *       Puesto con la misma guarda aciclica (422 en ciclo). Audita.</li>
 *   <li><strong>asignarEmpleado (Req 61.2):</strong> valida Empleado y Puesto
 *       (404); persiste la asignacion; audita.</li>
 *   <li><strong>registrarEvaluacion (Req 61.3, 61.8):</strong> valida el Empleado
 *       (404) y la calificacion (escala); persiste conservando el historial;
 *       audita.</li>
 *   <li><strong>consultarOrganigrama (Req 61.1):</strong> modelo derivado de solo
 *       lectura del bosque de Puestos del tenant.</li>
 *   <li><strong>listarPuestos / listarEvaluaciones (Req 61.4):</strong> listados
 *       paginados (20/100) con filtros.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant y auditoria (Req 23, 61.5)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion se registra via {@link AuditoriaPort} como
 * evento de tenant con el actor del contexto de seguridad. Un acceso a un recurso
 * de otro tenant devuelve 404 y se audita como intento de acceso cruzado
 * (Req 4.3, 23.3).</p>
 */
@Service
public class ServicioOrganizacion {

    /** Tipo de recurso de auditoria/RBAC del Puesto. */
    static final String RECURSO_PUESTO = "puesto";

    /** Tipo de recurso de auditoria/RBAC de la asignacion de Puesto. */
    static final String RECURSO_ASIGNACION = "asignacion_puesto";

    /** Tipo de recurso de auditoria/RBAC de la Evaluacion_Desempeno. */
    static final String RECURSO_EVALUACION = "evaluacion_desempeno";

    /** Tipo de recurso de auditoria del Empleado (para el acceso cruzado). */
    static final String RECURSO_EMPLEADO = "empleado";

    private final PuestoRepository puestoRepository;
    private final AsignacionPuestoRepository asignacionRepository;
    private final EvaluacionDesempenoRepository evaluacionRepository;
    private final EmpleadoRepository empleadoRepository;
    private final AuditoriaPort auditoria;

    public ServicioOrganizacion(PuestoRepository puestoRepository,
                                AsignacionPuestoRepository asignacionRepository,
                                EvaluacionDesempenoRepository evaluacionRepository,
                                EmpleadoRepository empleadoRepository,
                                AuditoriaPort auditoria) {
        this.puestoRepository = puestoRepository;
        this.asignacionRepository = asignacionRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.empleadoRepository = empleadoRepository;
        this.auditoria = auditoria;
    }

    /**
     * Crea un Puesto y lo situa en la jerarquia (Req 61.1, 61.7). Si se indica un
     * superior, este debe existir dentro del tenant (404) y la arista
     * {@code (nuevoPuesto -> superior)} no debe introducir un ciclo (422). Como el
     * Puesto aun no existe, el unico ciclo posible es referirse a un superior
     * inexistente/inaccesible (404) o un auto-superior, que la fabrica ya rechaza.
     *
     * @param comando datos del Puesto.
     * @return el DTO del Puesto creado.
     * @throws ReglaNegocioException        si los datos son invalidos o la
     *                                      jerarquia formaria un ciclo (422).
     * @throws RecursoNoEncontradoException si el superior indicado no es accesible
     *                                      (404).
     */
    @Transactional
    public PuestoDto crearPuesto(CrearPuestoCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos del Puesto son obligatorios.");
        }

        UUID superiorId = comando.puestoSuperiorId();
        if (superiorId != null) {
            // El superior debe existir y ser accesible dentro del tenant (Req 61.1).
            cargarPuestoActivo(superiorId, actor);
        }

        Puesto puesto = Puesto.crear(
                comando.nombre(), comando.descripcion(), superiorId, actor);

        // Guarda aciclica (Req 61.7). Aun sin persistir el nuevo Puesto, se valida
        // la arista propuesta sobre la jerarquia actual mas el propio Puesto.
        if (superiorId != null) {
            List<PuestoJerarquia> jerarquia = jerarquiaActual();
            jerarquia.add(new PuestoJerarquia(puesto.getId(), puesto.getNombre(), superiorId));
            if (GrafoOrganigrama.introduciriaCiclo(jerarquia, puesto.getId(), superiorId)) {
                throw new ReglaNegocioException(
                        "La jerarquia de Puestos no puede formar ciclos (jerarquia invalida).");
            }
        }

        Puesto guardado = puestoRepository.save(puesto);
        auditarPuesto(actor, "crear", guardado.getId(),
                "alta de puesto '" + guardado.getNombre() + "'"
                        + (superiorId != null ? " bajo el superior " + superiorId : " como raiz"));
        return PuestoDto.de(guardado);
    }

    /**
     * Mueve un Puesto en la jerarquia cambiando su superior directo (Req 61.1,
     * 61.7). El Puesto y, en su caso, el nuevo superior deben ser accesibles (404).
     * Si la arista {@code (puesto -> nuevoSuperior)} introdujera un ciclo, se
     * rechaza (422). Un {@code nuevoSuperiorId} nulo deja el Puesto como raiz.
     *
     * @param puestoId        identificador del Puesto a mover.
     * @param nuevoSuperiorId nuevo superior directo; {@code null} para dejarlo raiz.
     * @return el DTO del Puesto actualizado.
     * @throws RecursoNoEncontradoException si el Puesto o el nuevo superior no son
     *                                      accesibles (404).
     * @throws ReglaNegocioException        si la jerarquia formaria un ciclo (422).
     */
    @Transactional
    public PuestoDto moverPuesto(UUID puestoId, UUID nuevoSuperiorId) {
        String actor = actorActual();
        Puesto puesto = cargarPuestoActivo(puestoId, actor);
        if (nuevoSuperiorId != null) {
            cargarPuestoActivo(nuevoSuperiorId, actor);
            if (GrafoOrganigrama.introduciriaCiclo(jerarquiaActual(), puesto.getId(), nuevoSuperiorId)) {
                throw new ReglaNegocioException(
                        "La jerarquia de Puestos no puede formar ciclos (jerarquia invalida).");
            }
        }
        puesto.cambiarSuperior(nuevoSuperiorId, actor);
        Puesto guardado = puestoRepository.save(puesto);
        auditarPuesto(actor, "actualizar", guardado.getId(),
                "cambio de superior del puesto '" + guardado.getNombre() + "' a "
                        + (nuevoSuperiorId != null ? nuevoSuperiorId.toString() : "raiz"));
        return PuestoDto.de(guardado);
    }

    /**
     * Consulta puntual de un Puesto activo del tenant (Req 4.3, 23.3). Un Puesto
     * inexistente, inactivo o de otro tenant produce 404 y se audita el intento.
     *
     * @param puestoId identificador del Puesto.
     * @return el DTO del Puesto.
     * @throws RecursoNoEncontradoException si el Puesto no es accesible (404).
     */
    @Transactional(readOnly = true)
    public PuestoDto consultarPuesto(UUID puestoId) {
        String actor = actorActual();
        return PuestoDto.de(cargarPuestoActivo(puestoId, actor));
    }

    /**
     * Listado paginado de Puestos del tenant, filtrable por nombre (contiene, sin
     * distinguir mayusculas) y por estado (Req 61.4).
     *
     * @param nombre   subcadena a buscar en el nombre; {@code null}/blanco no filtra.
     * @param activo   estado a filtrar; {@code null} no filtra por estado.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Puestos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<PuestoDto> listarPuestos(String nombre, Boolean activo, Pageable pageable) {
        String criterio = (nombre == null) ? "" : nombre.strip().toLowerCase(Locale.ROOT);
        return puestoRepository.buscarConFiltros(criterio, activo, pageable).map(PuestoDto::de);
    }

    /**
     * Asigna un Empleado a un Puesto (Req 61.2). El Empleado (activo) y el Puesto
     * (activo) deben ser accesibles dentro del tenant (404). Persiste la asignacion
     * y audita.
     *
     * @param comando datos de la asignacion.
     * @return el DTO de la asignacion creada.
     * @throws RecursoNoEncontradoException si el Empleado o el Puesto no son
     *                                      accesibles (404).
     * @throws ReglaNegocioException        si los datos son invalidos (422).
     */
    @Transactional
    public AsignacionPuestoDto asignarEmpleado(AsignarEmpleadoCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la asignacion son obligatorios.");
        }
        Empleado empleado = cargarEmpleadoActivo(comando.empleadoId(), actor);
        Puesto puesto = cargarPuestoActivo(comando.puestoId(), actor);

        AsignacionPuesto asignacion = AsignacionPuesto.asignar(
                empleado.getId(), puesto.getId(), comando.fechaInicio(), actor);
        AsignacionPuesto guardada = asignacionRepository.save(asignacion);

        auditarAsignacion(actor, "crear", guardada.getId(),
                "asignacion del empleado " + empleado.getId()
                        + " al puesto '" + puesto.getNombre() + "' [" + puesto.getId() + "]");
        return AsignacionPuestoDto.de(guardada);
    }

    /**
     * Registra una Evaluacion_Desempeno de un Empleado en un periodo (Req 61.3,
     * 61.8), conservando el historial. El Empleado (activo) debe ser accesible
     * (404) y la calificacion debe estar dentro de la escala (422).
     *
     * @param comando datos de la evaluacion.
     * @return el DTO de la evaluacion creada.
     * @throws RecursoNoEncontradoException si el Empleado no es accesible (404).
     * @throws ReglaNegocioException        si los datos son invalidos o la
     *                                      calificacion esta fuera de escala (422).
     */
    @Transactional
    public EvaluacionDesempenoDto registrarEvaluacion(RegistrarEvaluacionCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Evaluacion_Desempeno son obligatorios.");
        }
        Empleado empleado = cargarEmpleadoActivo(comando.empleadoId(), actor);

        EvaluacionDesempeno evaluacion = EvaluacionDesempeno.registrar(
                empleado.getId(), comando.periodo(), comando.calificacion(),
                comando.comentarios(), actor);
        EvaluacionDesempeno guardada = evaluacionRepository.save(evaluacion);

        auditarEvaluacion(actor, "crear", guardada.getId(),
                "registrada evaluacion de desempeno del empleado " + empleado.getId()
                        + " en el periodo " + guardada.getPeriodo()
                        + " (calificacion=" + guardada.getCalificacion() + ")");
        return EvaluacionDesempenoDto.de(guardada);
    }

    /**
     * Listado paginado de Evaluacion_Desempeno del tenant, filtrable por Empleado y
     * por periodo (Req 61.4). Conserva el historial (varias por Empleado/periodo).
     *
     * @param empleadoId Empleado a filtrar; {@code null} no filtra.
     * @param periodo    periodo a filtrar; {@code null}/blanco no filtra.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de Evaluacion_Desempeno como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<EvaluacionDesempenoDto> listarEvaluaciones(UUID empleadoId, String periodo,
                                                           Pageable pageable) {
        String periodoFiltro = (periodo == null || periodo.isBlank()) ? null : periodo.strip();
        return evaluacionRepository.buscarConFiltros(empleadoId, periodoFiltro, pageable)
                .map(EvaluacionDesempenoDto::de);
    }

    /**
     * Deriva el organigrama del tenant como un bosque de nodos de solo lectura a
     * partir de los Puestos activos (Req 61.1). No modifica los datos de origen.
     *
     * @return la lista de nodos raiz del organigrama.
     */
    @Transactional(readOnly = true)
    public List<OrganigramaNodoDto> consultarOrganigrama() {
        List<OrganigramaNodo> bosque = GrafoOrganigrama.derivar(jerarquiaActual());
        return bosque.stream().map(OrganigramaNodoDto::de).toList();
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Vista de jerarquia (arista puesto -&gt; superior) de todos los Puestos
     * activos del tenant vigente, base de la validacion aciclica (Req 61.7) y de la
     * derivacion del organigrama (Req 61.1).
     */
    private List<PuestoJerarquia> jerarquiaActual() {
        List<PuestoJerarquia> jerarquia = new java.util.ArrayList<>();
        for (Puesto puesto : puestoRepository.findByActivoTrue()) {
            jerarquia.add(new PuestoJerarquia(
                    puesto.getId(), puesto.getNombre(), puesto.getPuestoSuperiorId()));
        }
        return jerarquia;
    }

    /**
     * Carga un Puesto activo por id dentro del tenant vigente; si no es accesible
     * audita el intento y lanza 404 (Req 4.3, 23.3).
     */
    private Puesto cargarPuestoActivo(UUID puestoId, String actor) {
        if (puestoId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Puesto solicitado.");
        }
        return puestoRepository.findByIdAndActivoTrue(puestoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_PUESTO, puestoId);
                    throw new RecursoNoEncontradoException("No se encontro el Puesto solicitado.");
                });
    }

    /**
     * Carga un Empleado activo por id dentro del tenant vigente; si no es accesible
     * audita el intento y lanza 404 (Req 4.3, 23.3, 61.6).
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

    private void auditarPuesto(String actor, String accion, UUID puestoId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_PUESTO,
                detalle + " [id=" + puestoId + "]", null, null));
    }

    private void auditarAsignacion(String actor, String accion, UUID asignacionId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_ASIGNACION,
                detalle + " [id=" + asignacionId + "]", null, null));
    }

    private void auditarEvaluacion(String actor, String accion, UUID evaluacionId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_EVALUACION,
                detalle + " [id=" + evaluacionId + "]", null, null));
    }

    /**
     * Audita un intento de acceso a un recurso no accesible (inexistente o de otro
     * tenant), antes de responder 404 (Req 4.3, 23.3).
     */
    private void auditarAccesoCruzado(String actor, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", recurso,
                "intento de acceso a " + recurso + " no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    /**
     * Resuelve el identificador del actor autenticado para la auditoria; si no hay
     * contexto de seguridad, usa "sistema".
     */
    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
