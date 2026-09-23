package com.dessti.crm.vertical.anuncios.instalacion.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.EvidenciaInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.OrdenTrabajoInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.PendienteInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.EvidenciaInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.OrdenTrabajoInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.PendienteInstalacion;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoCompletadoPort;
import com.dessti.crm.operacion.produccion.application.OrdenFabricacionTerminadaPort;
import com.dessti.crm.vertical.anuncios.permiso.application.PermisoAprobadoPort;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las
 * {@link OrdenTrabajoInstalacion} (OTI) y su registro de avance (Req 19). Replica
 * el patron establecido por {@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones (Req 19)</h2>
 * <ul>
 *   <li><strong>programar (Req 19.1, 19.2, 19.3):</strong> crea una OTI a partir de
 *       una Orden_Fabricacion <em>si y solo si</em> se cumplen las precondiciones
 *       (ver mas abajo); la crea en estado {@code programada} vinculada a la OF, al
 *       Sitio, a la Cuadrilla y al Cliente (denormalizado), y audita.</li>
 *   <li><strong>registrarAvance (Req 19.4):</strong> agrega pendientes, adjunta
 *       evidencias fotograficas y/o marca pendientes como resueltos, conservandolos
 *       vinculados a la OTI; audita.</li>
 *   <li><strong>cambiarEstado (Req 19.5, 19.6, 19.8):</strong> aplica la maquina de
 *       estados pura (409 si la transicion es invalida); ademas, antes de pasar a
 *       {@code completada} verifica la guarda del Req 19.6 (no debe quedar ningun
 *       pendiente sin resolver). Audita el estado anterior y el nuevo.</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria del intento si no es
 *       accesible.</li>
 *   <li><strong>listar (Req 19.7):</strong> listado paginado (20/100) con filtros
 *       por estado, Cuadrilla y Cliente; sin coincidencias devuelve una pagina
 *       vacia con total 0.</li>
 * </ul>
 *
 * <h2>Precondiciones de programacion (Req 19.2, 19.3, en orden)</h2>
 * <ol>
 *   <li>La Orden_Fabricacion existe en el tenant (via
 *       {@link OrdenFabricacionTerminadaPort#clienteDeOrden(UUID)}); si no, 404 y
 *       auditoria del acceso cruzado (Req 23.3).</li>
 *   <li>La Orden_Fabricacion esta en estado {@code terminada} (Req 19.2, via
 *       {@link OrdenFabricacionTerminadaPort#estaTerminada(UUID)}); si no,
 *       {@link ReglaNegocioException} (422) con el mensaje "se requiere una
 *       Orden_Fabricacion terminada".</li>
 *   <li>El Sitio de la OTI tiene un Levantamiento_Sitio {@code completado} (Req 19.3,
 *       via {@link LevantamientoCompletadoPort}); si no,
 *       {@link ReglaNegocioException} (422) con el mensaje "el Sitio requiere un
 *       Levantamiento_Sitio completado".</li>
 *   <li>El Sitio de la OTI tiene un Permiso_Instalacion {@code aprobado} (Req 19.3,
 *       via {@link PermisoAprobadoPort}); si no, {@link ReglaNegocioException} (422)
 *       con el mensaje "el Sitio requiere un Permiso_Instalacion aprobado".</li>
 * </ol>
 * En todos los casos de rechazo NO se crea ninguna OTI (Req 19.2, 19.3).
 *
 * <h2>Guarda de cierre (Req 19.6)</h2>
 * <p>El cierre (transicion a {@code completada}) se rechaza con
 * {@link ReglaNegocioException} (422) si la Lista_Pendientes de la OTI tiene al
 * menos un elemento sin resolver. El mensaje <strong>enumera las descripciones</strong>
 * de los pendientes sin resolver (p. ej. "no se puede completar: pendientes por
 * resolver: [«fijar anclas», «conectar acometida»]"), para indicar exactamente que
 * falta (Req 8.5, diseno §C2). Esta guarda vive en el servicio (no en el dominio puro) porque requiere
 * consultar la tabla hija {@code pendiente_instalacion}, del mismo modo que las
 * precondiciones de generacion de {@code ServicioOrdenesFabricacion} viven en la
 * aplicacion. Se conserva el estado actual sin modificarlo. La validez de la
 * transicion (Req 19.5) se comprueba <strong>antes</strong> que esta guarda: un
 * intento de cierre desde un estado desde el que {@code completada} no es
 * alcanzable (por ejemplo {@code programada}) se rechaza como transicion invalida
 * (409), de modo que el mensaje de pendientes solo aflora cuando el cierre seria
 * por lo demas valido.</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 19.8)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se registra via
 * {@link AuditoriaPort} como evento de tenant con el actor derivado del contexto;
 * la creacion y el cambio de estado incluyen el estado anterior y el nuevo
 * (Req 19.8).</p>
 */
@Service
public class ServicioOrdenesTrabajoInstalacion {

    /** Tipo de recurso de auditoria/RBAC de la Orden_Trabajo_Instalacion. */
    static final String RECURSO_OTI = "orden_trabajo_instalacion";

    private final OrdenTrabajoInstalacionRepository ordenRepository;
    private final PendienteInstalacionRepository pendienteRepository;
    private final EvidenciaInstalacionRepository evidenciaRepository;
    private final OrdenFabricacionTerminadaPort ordenFabricacionTerminada;
    private final LevantamientoCompletadoPort levantamientoCompletado;
    private final PermisoAprobadoPort permisoAprobado;
    private final AuditoriaPort auditoria;

    public ServicioOrdenesTrabajoInstalacion(OrdenTrabajoInstalacionRepository ordenRepository,
                                             PendienteInstalacionRepository pendienteRepository,
                                             EvidenciaInstalacionRepository evidenciaRepository,
                                             OrdenFabricacionTerminadaPort ordenFabricacionTerminada,
                                             LevantamientoCompletadoPort levantamientoCompletado,
                                             PermisoAprobadoPort permisoAprobado,
                                             AuditoriaPort auditoria) {
        this.ordenRepository = ordenRepository;
        this.pendienteRepository = pendienteRepository;
        this.evidenciaRepository = evidenciaRepository;
        this.ordenFabricacionTerminada = ordenFabricacionTerminada;
        this.levantamientoCompletado = levantamientoCompletado;
        this.permisoAprobado = permisoAprobado;
        this.auditoria = auditoria;
    }

    /**
     * Programa una Orden_Trabajo_Instalacion a partir de una Orden_Fabricacion
     * terminada, aplicando las precondiciones del Req 19.2 y 19.3. En caso de exito
     * crea la OTI en estado {@code programada} vinculada a la OF, al Sitio, a la
     * Cuadrilla y a su Cliente, y devuelve su DTO con el identificador (Req 19.1).
     *
     * @param comando datos de programacion (OF, Sitio, Cuadrilla, fecha).
     * @return el DTO de la OTI creada (estado {@code programada}).
     * @throws RecursoNoEncontradoException si la Orden_Fabricacion no existe en el
     *         tenant (404, Req 23.3).
     * @throws ReglaNegocioException si la OF no esta terminada (422, Req 19.2), o el
     *         Sitio no tiene Levantamiento completado ni Permiso aprobado (422,
     *         Req 19.3), o faltan datos obligatorios (422).
     */
    @Transactional
    public OrdenTrabajoInstalacionDto programar(ProgramarOrdenTrabajoInstalacionCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de programacion son obligatorios.");
        }
        UUID ordenFabricacionId = comando.ordenFabricacionId();
        UUID sitioId = comando.sitioId();
        if (ordenFabricacionId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Trabajo_Instalacion debe asociarse a una Orden_Fabricacion existente.");
        }
        if (sitioId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Trabajo_Instalacion debe asociarse a un Sitio.");
        }

        // Precondicion 0: la Orden_Fabricacion existe en el tenant (Req 23.3). La
        // consulta del Cliente devuelve vacio si la OF no es accesible.
        UUID clienteId = ordenFabricacionTerminada.clienteDeOrden(ordenFabricacionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, "orden_fabricacion", ordenFabricacionId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Orden_Fabricacion indicada para la instalacion.");
                });

        // Precondicion 1 (Req 19.2): la Orden_Fabricacion esta 'terminada'.
        if (!ordenFabricacionTerminada.estaTerminada(ordenFabricacionId)) {
            throw new ReglaNegocioException("se requiere una Orden_Fabricacion terminada");
        }

        // Precondicion 2 (Req 19.3): el Sitio tiene un Levantamiento completado.
        if (!levantamientoCompletado.sitioTieneLevantamientoCompletado(sitioId)) {
            throw new ReglaNegocioException("el Sitio requiere un Levantamiento_Sitio completado");
        }

        // Precondicion 3 (Req 19.3): el Sitio tiene un Permiso_Instalacion aprobado.
        if (!permisoAprobado.sitioTienePermisoAprobado(sitioId)) {
            throw new ReglaNegocioException("el Sitio requiere un Permiso_Instalacion aprobado");
        }

        OrdenTrabajoInstalacion orden = OrdenTrabajoInstalacion.programar(
                ordenFabricacionId, sitioId, comando.cuadrillaId(), clienteId,
                comando.fechaProgramada(), actor);
        OrdenTrabajoInstalacion guardada = ordenRepository.save(orden);
        auditar(actor, "crear", guardada.getId(),
                "programada Orden_Trabajo_Instalacion en estado '"
                        + guardada.getEstado().valorBd() + "' [orden_fabricacion="
                        + ordenFabricacionId + ", sitio=" + sitioId + ", cuadrilla="
                        + guardada.getCuadrillaId() + ", cliente=" + clienteId + "]",
                null, guardada.getEstado().valorBd());
        return OrdenTrabajoInstalacionDto.de(guardada);
    }

    /**
     * Registra el avance de una Orden_Trabajo_Instalacion (Req 19.4): agrega nuevas
     * entradas de Lista_Pendientes, adjunta evidencias fotograficas y/o marca
     * pendientes como resueltos, conservandolos vinculados a la OTI. Audita la
     * operacion.
     *
     * @param ordenId identificador de la Orden_Trabajo_Instalacion.
     * @param comando pendientes nuevos, evidencias y pendientes a resolver.
     * @return el DTO de la OTI (su estado no cambia con esta operacion).
     * @throws RecursoNoEncontradoException si la OTI no es accesible (404).
     * @throws ReglaNegocioException si alguna descripcion/referencia esta vacia
     *         (422), o si un pendiente a resolver no pertenece a la OTI (404/422).
     */
    @Transactional
    public OrdenTrabajoInstalacionDto registrarAvance(UUID ordenId, RegistrarAvanceCommand comando) {
        String actor = actorActual();
        OrdenTrabajoInstalacion orden = cargar(ordenId, actor);
        if (comando == null) {
            return OrdenTrabajoInstalacionDto.de(orden);
        }

        List<String> nuevosPendientes = normalizar(comando.nuevosPendientes());
        List<String> evidencias = normalizar(comando.evidencias());
        List<UUID> pendientesResueltos = normalizarIds(comando.pendientesResueltos());

        for (String descripcion : nuevosPendientes) {
            pendienteRepository.save(PendienteInstalacion.paraOrden(orden, descripcion, actor));
        }
        for (String url : evidencias) {
            evidenciaRepository.save(EvidenciaInstalacion.paraOrden(orden, url, actor));
        }
        for (UUID pendienteId : pendientesResueltos) {
            PendienteInstalacion pendiente = pendienteRepository.findById(pendienteId)
                    .filter(p -> p.getOrdenTrabajoInstalacionId().equals(orden.getId()))
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No se encontro el pendiente indicado en la Orden_Trabajo_Instalacion."));
            pendiente.resolver(actor);
            pendienteRepository.save(pendiente);
        }

        auditar(actor, "cambiar_estado", orden.getId(),
                "registro de avance [pendientes_nuevos=" + nuevosPendientes.size()
                        + ", evidencias=" + evidencias.size()
                        + ", pendientes_resueltos=" + pendientesResueltos.size() + "]",
                null, null);
        return OrdenTrabajoInstalacionDto.de(orden);
    }

    /**
     * Cambia el estado de una Orden_Trabajo_Instalacion aplicando la maquina de
     * estados pura (Req 19.5) y la guarda de cierre del Req 19.6, auditando el
     * estado anterior y el nuevo (Req 19.8).
     *
     * @param ordenId     identificador de la Orden_Trabajo_Instalacion.
     * @param nuevoEstado etiqueta del estado destino; obligatoria (Req 19.5).
     * @return el DTO de la OTI con su nuevo estado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si el estado es nulo/desconocido (422), o si se
     *         intenta completar con pendientes sin resolver (422, Req 19.6).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la
     *         transicion no esta permitida (409, Req 19.5).
     */
    @Transactional
    public OrdenTrabajoInstalacionDto cambiarEstado(UUID ordenId, String nuevoEstado) {
        String actor = actorActual();
        EstadoOrdenTrabajoInstalacion destino = interpretarEstado(nuevoEstado);
        OrdenTrabajoInstalacion orden = cargar(ordenId, actor);
        EstadoOrdenTrabajoInstalacion anterior = orden.getEstado();

        // Orden de comprobaciones (Req 19.5 antes que Req 19.6): primero se valida
        // que la transicion sea legal en la maquina de estados pura; una transicion
        // no permitida (por ejemplo saltar de 'programada' a 'completada' sin pasar
        // por 'en_curso', o cualquier transicion desde un estado final) se rechaza
        // con TransicionInvalidaException (409) y el estado se conserva. Solo cuando
        // la transicion es en si valida se aplica la guarda de negocio del cierre.
        if (!anterior.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + anterior.valorBd()
                            + "' a '" + destino.valorBd() + "'.");
        }

        // Guarda de cierre informativa (Req 19.6, Req 8.5; diseno §C2): no completar
        // mientras haya pendientes sin resolver. Se aplica una vez confirmada la
        // validez de la transicion, de modo que el mensaje solo aflora cuando el
        // cierre seria por lo demas valido (transicion 'en_curso' -> 'completada').
        // A diferencia de la version anterior (mensaje generico "existen pendientes
        // por resolver"), ahora se recuperan los pendientes sin resolver y se enumera
        // su descripcion en el 422 para indicar exactamente que falta. El estado
        // actual se conserva sin modificarlo.
        if (destino == EstadoOrdenTrabajoInstalacion.COMPLETADA) {
            List<PendienteInstalacion> sinResolver = pendienteRepository
                    .findByOrdenTrabajoInstalacionIdAndResueltoFalseOrderByCreatedAtAsc(orden.getId());
            if (!sinResolver.isEmpty()) {
                String descripciones = sinResolver.stream()
                        .map(PendienteInstalacion::getDescripcion)
                        .map(descripcion -> "«" + descripcion + "»")
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new ReglaNegocioException(
                        "no se puede completar: pendientes por resolver: [" + descripciones + "]");
            }
        }

        orden.cambiarEstado(destino, actor);
        OrdenTrabajoInstalacion guardada = ordenRepository.save(orden);
        auditar(actor, "cambiar_estado", guardada.getId(),
                "cambio de estado '" + anterior.valorBd() + "' -> '" + destino.valorBd() + "'",
                anterior.valorBd(), destino.valorBd());
        return OrdenTrabajoInstalacionDto.de(guardada);
    }

    /**
     * Consulta puntual de una Orden_Trabajo_Instalacion del tenant (Req 23.3).
     *
     * @param ordenId identificador de la Orden_Trabajo_Instalacion.
     * @return el DTO de la OTI.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public OrdenTrabajoInstalacionDto consultar(UUID ordenId) {
        String actor = actorActual();
        return OrdenTrabajoInstalacionDto.de(cargar(ordenId, actor));
    }

    /**
     * Consulta puntual de una Orden_Trabajo_Instalacion del tenant enriquecida con
     * sus pendientes y evidencias vinculados (Req 8.1, 8.2, 8.3; diseno §C2). Las
     * listas son vacias si la OTI no tiene pendientes o evidencias. 404 + auditoria
     * del acceso cruzado si no es accesible (Req 8.6, 23.3).
     *
     * @param ordenId identificador de la Orden_Trabajo_Instalacion.
     * @return el DTO de detalle con sus pendientes y evidencias (listas vacias si no hay).
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public OrdenTrabajoInstalacionDetalleDto consultarDetalle(UUID ordenId) {
        String actor = actorActual();
        OrdenTrabajoInstalacion orden = cargar(ordenId, actor);
        return OrdenTrabajoInstalacionDetalleDto.de(
                orden, pendientesVinculados(orden.getId()), evidenciasVinculadas(orden.getId()));
    }

    /**
     * Devuelve los pendientes de la Lista_Pendientes de una
     * Orden_Trabajo_Instalacion accesible del tenant (Req 8.2; diseno §C2), cada uno
     * con su bandera {@code resuelto}. Verifica primero el acceso a la OTI (404 +
     * auditoria del acceso cruzado si no es accesible, Req 8.6, 23.3) y luego lee el
     * {@link PendienteInstalacionRepository} acotado al tenant vigente. Devuelve una
     * lista vacia cuando la OTI no tiene pendientes.
     *
     * @param ordenId identificador de la Orden_Trabajo_Instalacion.
     * @return la lista de DTOs de los pendientes vinculados (posiblemente vacia).
     * @throws RecursoNoEncontradoException si la OTI no es accesible (404).
     */
    @Transactional(readOnly = true)
    public List<PendienteInstalacionDto> pendientesDe(UUID ordenId) {
        String actor = actorActual();
        OrdenTrabajoInstalacion orden = cargar(ordenId, actor);
        return pendientesVinculados(orden.getId());
    }

    /**
     * Devuelve las evidencias fotograficas adjuntas a una Orden_Trabajo_Instalacion
     * accesible del tenant (Req 8.3; diseno §C2). Verifica primero el acceso a la
     * OTI (404 + auditoria del acceso cruzado si no es accesible, Req 8.6, 23.3) y
     * luego lee el {@link EvidenciaInstalacionRepository} acotado al tenant vigente.
     * Devuelve una lista vacia cuando la OTI no tiene evidencias.
     *
     * @param ordenId identificador de la Orden_Trabajo_Instalacion.
     * @return la lista de DTOs de las evidencias vinculadas (posiblemente vacia).
     * @throws RecursoNoEncontradoException si la OTI no es accesible (404).
     */
    @Transactional(readOnly = true)
    public List<EvidenciaInstalacionDto> evidenciasDe(UUID ordenId) {
        String actor = actorActual();
        OrdenTrabajoInstalacion orden = cargar(ordenId, actor);
        return evidenciasVinculadas(orden.getId());
    }

    private List<PendienteInstalacionDto> pendientesVinculados(UUID ordenId) {
        return pendienteRepository
                .findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(ordenId).stream()
                .map(PendienteInstalacionDto::de)
                .toList();
    }

    private List<EvidenciaInstalacionDto> evidenciasVinculadas(UUID ordenId) {
        return evidenciaRepository
                .findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(ordenId).stream()
                .map(EvidenciaInstalacionDto::de)
                .toList();
    }

    /**
     * Listado paginado de Ordenes de Trabajo de Instalacion del tenant con filtros
     * opcionales por estado, Cuadrilla y Cliente (Req 19.7). Un filtro nulo no
     * restringe; sin coincidencias se devuelve una pagina vacia con total 0.
     *
     * @param estado      etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param cuadrillaId Cuadrilla a filtrar; {@code null} no filtra.
     * @param clienteId   Cliente a filtrar; {@code null} no filtra.
     * @param pageable    parametros de paginacion ya acotados (20/100).
     * @return la pagina de Ordenes de Trabajo de Instalacion como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<OrdenTrabajoInstalacionDto> listar(String estado, UUID cuadrillaId,
                                                   UUID clienteId, Pageable pageable) {
        EstadoOrdenTrabajoInstalacion filtro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return ordenRepository.buscarConFiltros(filtro, cuadrillaId, clienteId, pageable)
                .map(OrdenTrabajoInstalacionDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private OrdenTrabajoInstalacion cargar(UUID ordenId, String actor) {
        if (ordenId == null) {
            throw new RecursoNoEncontradoException(
                    "No se encontro la Orden_Trabajo_Instalacion solicitada.");
        }
        return ordenRepository.findById(ordenId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_OTI, ordenId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Orden_Trabajo_Instalacion solicitada.");
                });
    }

    private EstadoOrdenTrabajoInstalacion interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        try {
            return EstadoOrdenTrabajoInstalacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException(
                    "Estado de Orden_Trabajo_Instalacion desconocido: " + etiqueta);
        }
    }

    private static List<String> normalizar(List<String> valores) {
        List<String> resultado = new ArrayList<>();
        if (valores == null) {
            return resultado;
        }
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                resultado.add(valor);
            }
        }
        return resultado;
    }

    private static List<UUID> normalizarIds(List<UUID> valores) {
        List<UUID> resultado = new ArrayList<>();
        if (valores == null) {
            return resultado;
        }
        for (UUID valor : valores) {
            if (valor != null) {
                resultado.add(valor);
            }
        }
        return resultado;
    }

    private void auditar(String actor, String accion, UUID ordenId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_OTI,
                detalle + " [id=" + ordenId + "]", valorAnterior, valorNuevo));
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
