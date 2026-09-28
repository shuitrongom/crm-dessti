package com.dessti.crm.comercial.actividad.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.actividad.adapter.out.persistence.ActividadRepository;
import com.dessti.crm.comercial.actividad.domain.Actividad;
import com.dessti.crm.comercial.actividad.domain.EstadoActividad;
import com.dessti.crm.comercial.actividad.domain.TipoActividad;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las {@link Actividad}
 * de seguimiento comercial (V79). Replica el patron establecido por
 * {@code ServicioOportunidades}/{@code ServicioClientes}.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>registrar:</strong> verifica que el Cliente exista y este activo
 *       (404 si no); si se vincula a una Oportunidad, verifica que exista y
 *       pertenezca a ese Cliente (404 si no); crea la Actividad con su estado
 *       inicial segun el tipo, persiste y audita.</li>
 *   <li><strong>completar / cancelar:</strong> aplican la maquina de estados pura
 *       (409 si la transicion es invalida) y auditan el estado anterior y el
 *       nuevo.</li>
 *   <li><strong>reprogramar:</strong> ajusta fecha/vencimiento de una Actividad no
 *       final (422 si ya cerro).</li>
 *   <li><strong>editar:</strong> corrige asunto/descripcion sin alterar el ciclo
 *       de vida.</li>
 *   <li><strong>asignarResponsable:</strong> registra o limpia al Usuario a cargo.</li>
 *   <li><strong>eliminar:</strong> borrado fisico de la Actividad (una anotacion
 *       erronea se retira), auditado.</li>
 *   <li><strong>consultar / listar / timeline / tareas pendientes:</strong>
 *       lecturas acotadas al tenant.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion de escritura se registra via
 * {@link AuditoriaPort} como evento de tenant con el actor derivado del contexto
 * de seguridad.</p>
 */
@Service
public class ServicioActividades {

    /** Tipo de recurso de auditoria/RBAC de la Actividad. */
    static final String RECURSO_ACTIVIDAD = "actividad";

    private final ActividadRepository actividadRepository;
    private final ClienteExistentePort clienteExistente;
    private final OportunidadDeClientePort oportunidadDeCliente;
    private final AuditoriaPort auditoria;

    public ServicioActividades(ActividadRepository actividadRepository,
                               ClienteExistentePort clienteExistente,
                               OportunidadDeClientePort oportunidadDeCliente,
                               AuditoriaPort auditoria) {
        this.actividadRepository = actividadRepository;
        this.clienteExistente = clienteExistente;
        this.oportunidadDeCliente = oportunidadDeCliente;
        this.auditoria = auditoria;
    }

    /**
     * Registra una Actividad de seguimiento asociada a un Cliente existente y,
     * opcionalmente, a una Oportunidad de ese Cliente.
     *
     * @param comando datos de la Actividad a registrar.
     * @return el DTO de la Actividad creada.
     * @throws RecursoNoEncontradoException si el Cliente no existe/activo, o si la
     *         Oportunidad indicada no existe o no pertenece al Cliente (404).
     * @throws ReglaNegocioException si faltan o son invalidos los datos (422).
     */
    @Transactional
    public ActividadDto registrar(CrearActividadCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Actividad son obligatorios.");
        }
        if (comando.clienteId() == null) {
            throw new ReglaNegocioException("La Actividad debe asociarse a un Cliente existente.");
        }
        if (!clienteExistente.existeClienteActivo(comando.clienteId())) {
            auditarAccesoCruzado(actor, "cliente", comando.clienteId());
            throw new RecursoNoEncontradoException("No se encontro el Cliente indicado para la Actividad.");
        }
        if (comando.oportunidadId() != null
                && !oportunidadDeCliente.existeOportunidadDeCliente(comando.oportunidadId(), comando.clienteId())) {
            auditarAccesoCruzado(actor, "oportunidad", comando.oportunidadId());
            throw new RecursoNoEncontradoException(
                    "La Oportunidad indicada no existe o no pertenece al Cliente de la Actividad.");
        }
        TipoActividad tipo = interpretarTipo(comando.tipo());
        Actividad actividad = Actividad.crear(
                comando.clienteId(), comando.oportunidadId(), tipo,
                comando.asunto(), comando.descripcion(), comando.fechaProgramada(),
                comando.vencimiento(), comando.responsableUsuarioId(), actor);
        Actividad guardada = actividadRepository.save(actividad);
        auditar(actor, "crear", guardada.getId(),
                "registrada actividad '" + guardada.getTipo().valorBd() + "': '"
                        + guardada.getAsunto() + "'", null, null);
        return ActividadDto.de(guardada);
    }

    /**
     * Marca una Actividad como completada (sella el instante de completado).
     *
     * @param actividadId identificador de la Actividad.
     * @return el DTO actualizado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si esta
     *         cancelada (409).
     */
    @Transactional
    public ActividadDto completar(UUID actividadId) {
        String actor = actorActual();
        Actividad actividad = cargar(actividadId, actor);
        EstadoActividad anterior = actividad.getEstado();
        actividad.completar(actor);
        Actividad guardada = actividadRepository.save(actividad);
        auditar(actor, "completar", guardada.getId(),
                "actividad completada", anterior.valorBd(), guardada.getEstado().valorBd());
        return ActividadDto.de(guardada);
    }

    /**
     * Marca una Actividad como cancelada.
     *
     * @param actividadId identificador de la Actividad.
     * @return el DTO actualizado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si esta
     *         completada (409).
     */
    @Transactional
    public ActividadDto cancelar(UUID actividadId) {
        String actor = actorActual();
        Actividad actividad = cargar(actividadId, actor);
        EstadoActividad anterior = actividad.getEstado();
        actividad.cancelar(actor);
        Actividad guardada = actividadRepository.save(actividad);
        auditar(actor, "cancelar", guardada.getId(),
                "actividad cancelada", anterior.valorBd(), guardada.getEstado().valorBd());
        return ActividadDto.de(guardada);
    }

    /**
     * Reprograma la fecha y el vencimiento de una Actividad no finalizada.
     *
     * @param actividadId  identificador de la Actividad.
     * @param nuevaFecha   nueva fecha programada; obligatoria.
     * @param vencimiento  nuevo vencimiento; opcional.
     * @return el DTO actualizado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si ya es final o las fechas son invalidas (422).
     */
    @Transactional
    public ActividadDto reprogramar(UUID actividadId, Instant nuevaFecha, Instant vencimiento) {
        String actor = actorActual();
        Actividad actividad = cargar(actividadId, actor);
        actividad.reprogramar(nuevaFecha, vencimiento, actor);
        Actividad guardada = actividadRepository.save(actividad);
        auditar(actor, "reprogramar", guardada.getId(),
                "actividad reprogramada a " + guardada.getFechaProgramada(), null, null);
        return ActividadDto.de(guardada);
    }

    /**
     * Corrige el asunto y la descripcion de una Actividad.
     *
     * @param actividadId identificador de la Actividad.
     * @param comando     nuevos asunto y descripcion.
     * @return el DTO actualizado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si el asunto/descripcion son invalidos (422).
     */
    @Transactional
    public ActividadDto editar(UUID actividadId, EditarActividadCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Actividad son obligatorios.");
        }
        Actividad actividad = cargar(actividadId, actor);
        actividad.editarContenido(comando.asunto(), comando.descripcion(), actor);
        Actividad guardada = actividadRepository.save(actividad);
        auditar(actor, "actualizar", guardada.getId(),
                "actividad editada: '" + guardada.getAsunto() + "'", null, null);
        return ActividadDto.de(guardada);
    }

    /**
     * Asigna o limpia el Usuario responsable del seguimiento de una Actividad.
     *
     * @param actividadId identificador de la Actividad.
     * @param usuarioId   Usuario a cargo; {@code null} para desasignar.
     * @return el DTO actualizado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public ActividadDto asignarResponsable(UUID actividadId, UUID usuarioId) {
        String actor = actorActual();
        Actividad actividad = cargar(actividadId, actor);
        actividad.asignarResponsable(usuarioId, actor);
        Actividad guardada = actividadRepository.save(actividad);
        auditar(actor, "asignar_responsable", guardada.getId(),
                "asignado responsable [usuario=" + usuarioId + "]", null, null);
        return ActividadDto.de(guardada);
    }

    /**
     * Elimina fisicamente una Actividad del tenant (retira una anotacion erronea).
     * A diferencia del Cliente, la Actividad no tiene borrado logico: es un evento
     * del historial que, si se captura por error, se retira por completo.
     *
     * @param actividadId identificador de la Actividad.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public void eliminar(UUID actividadId) {
        String actor = actorActual();
        Actividad actividad = cargar(actividadId, actor);
        actividadRepository.delete(actividad);
        auditar(actor, "eliminar", actividadId,
                "eliminada actividad '" + actividad.getAsunto() + "'", null, null);
    }

    /**
     * Consulta puntual de una Actividad del tenant.
     *
     * @param actividadId identificador de la Actividad.
     * @return el DTO de la Actividad.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ActividadDto consultar(UUID actividadId) {
        String actor = actorActual();
        return ActividadDto.de(cargar(actividadId, actor));
    }

    /**
     * Listado paginado de Actividades del tenant con filtros opcionales por
     * Cliente, Oportunidad, tipo, estado y responsable. Un filtro nulo no
     * restringe. Ordenadas por fecha programada descendente (timeline).
     *
     * @param clienteId     Cliente a filtrar; {@code null} no filtra.
     * @param oportunidadId Oportunidad a filtrar; {@code null} no filtra.
     * @param tipo          etiqueta de tipo a filtrar; {@code null}/blanco no filtra.
     * @param estado        etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param responsableId Usuario responsable a filtrar; {@code null} no filtra.
     * @param pageable      parametros de paginacion ya acotados (20/100).
     * @return la pagina de Actividades como DTOs.
     * @throws ReglaNegocioException si la etiqueta de tipo/estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<ActividadDto> listar(UUID clienteId, UUID oportunidadId, String tipo,
                                     String estado, UUID responsableId, Pageable pageable) {
        TipoActividad tipoFiltro = (tipo == null || tipo.isBlank()) ? null : interpretarTipo(tipo);
        EstadoActividad estadoFiltro = (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return actividadRepository
                .buscarConFiltros(clienteId, oportunidadId, tipoFiltro, estadoFiltro, responsableId, pageable)
                .map(ActividadDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Actividad cargar(UUID actividadId, String actor) {
        if (actividadId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Actividad solicitada.");
        }
        return actividadRepository.findById(actividadId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_ACTIVIDAD, actividadId);
                    throw new RecursoNoEncontradoException("No se encontro la Actividad solicitada.");
                });
    }

    private TipoActividad interpretarTipo(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El tipo de la Actividad es obligatorio.");
        }
        try {
            return TipoActividad.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Tipo de Actividad desconocido: " + etiqueta);
        }
    }

    private EstadoActividad interpretarEstado(String etiqueta) {
        try {
            return EstadoActividad.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Actividad desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID actividadId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_ACTIVIDAD,
                detalle + " [id=" + actividadId + "]", valorAnterior, valorNuevo));
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
