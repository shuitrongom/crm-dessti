package com.dessti.crm.vertical.anuncios.mantenimiento.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.persistence.ContratoMantenimientoRepository;
import com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.persistence.TicketServicioRepository;
import com.dessti.crm.vertical.anuncios.mantenimiento.domain.AsignadoTipo;
import com.dessti.crm.vertical.anuncios.mantenimiento.domain.ContratoMantenimiento;
import com.dessti.crm.vertical.anuncios.mantenimiento.domain.EstadoTicketServicio;
import com.dessti.crm.vertical.anuncios.mantenimiento.domain.OrigenTicket;
import com.dessti.crm.vertical.anuncios.mantenimiento.domain.TicketServicio;
import com.dessti.crm.vertical.anuncios.mantenimiento.domain.TipoContratoMantenimiento;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el modulo de mantenimiento: el ciclo de vida
 * de los {@link ContratoMantenimiento} y los {@link TicketServicio} con SLA
 * (Req 20). Replica el patron establecido por {@code ServicioOrdenesFabricacion} y
 * {@code ServicioPermisos}.
 *
 * <h2>Operaciones (Req 20)</h2>
 * <ul>
 *   <li><strong>crearContrato (Req 20.1):</strong> valida el tipo y los tiempos del
 *       SLA (&gt; 0), crea el contrato asociado al Cliente, persiste y audita. La
 *       <em>existencia</em> del Cliente la garantiza la FK de V27: este submodulo no
 *       dispone (aun) de un puerto de lectura de Cliente, de modo que —como hizo el
 *       submodulo proyecto (V25)— se confia en la FK; una referencia a un Cliente
 *       inexistente se traduce a error de integridad.</li>
 *   <li><strong>generarTicket (Req 20.2):</strong> crea el ticket en estado
 *       {@code abierto} (origen {@code manual} o {@code preventivo}); si trae
 *       contrato, valida que exista en el tenant (404 si no). Audita (estado
 *       anterior {@code null}, nuevo {@code abierto}).</li>
 *   <li><strong>asignarTicket (Req 20.3):</strong> fija la asignacion (tecnico o
 *       Cuadrilla) y aplica {@code abierto -> asignado}; audita.</li>
 *   <li><strong>cambiarEstadoTicket (Req 20.4, 20.5, 20.6):</strong> aplica la
 *       maquina de estados (409 si la transicion es invalida); al pasar a
 *       {@code resuelto}, evalua el cumplimiento del SLA contra el contrato (si lo
 *       hay) y fija el instante de resolucion. Audita el estado anterior y el nuevo
 *       (Req 20.8).</li>
 *   <li><strong>consultar/listar (Req 20.7):</strong> listado paginado (20/100) con
 *       filtros por estado, por Cliente y por vencimiento del SLA.</li>
 * </ul>
 *
 * <h2>Evaluacion del SLA al resolver (Req 20.6)</h2>
 * <p>El calculo del cumplimiento vive en este servicio (no en el dominio puro)
 * porque necesita el Contrato_Mantenimiento asociado y el {@link Clock} inyectado.
 * Regla implementada: sea {@code horasTranscurridas = Duration.between(abiertoEn,
 * ahora)} en horas; {@code sla_respuesta_cumplido = horasTranscurridas <=
 * contrato.slaRespuestaHoras} y {@code sla_resolucion_cumplido = horasTranscurridas
 * <= contrato.slaResolucionHoras}. El requisito compara "el tiempo transcurrido
 * desde la apertura" con AMBOS tiempos (respuesta y resolucion); se usa el mismo
 * instante de resolucion como corte para las dos comparaciones. Si el ticket NO
 * tiene contrato, ambas banderas quedan {@code null} (sin SLA que evaluar; V27
 * DECISION 2).</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 20.8)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se registra via
 * {@link AuditoriaPort} como evento de tenant con el actor derivado del contexto;
 * el cambio de estado incluye el estado anterior y el nuevo (Req 20.8). Los
 * instantes UTC usan el {@link Clock} inyectado para ser deterministas en pruebas.</p>
 */
@Service
public class ServicioMantenimiento {

    /** Tipo de recurso de auditoria/RBAC del Contrato_Mantenimiento. */
    static final String RECURSO_CONTRATO = "contrato_mantenimiento";

    /** Tipo de recurso de auditoria/RBAC del Ticket_Servicio. */
    static final String RECURSO_TICKET = "ticket_servicio";

    private final ContratoMantenimientoRepository contratoRepository;
    private final TicketServicioRepository ticketRepository;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioMantenimiento(ContratoMantenimientoRepository contratoRepository,
                                 TicketServicioRepository ticketRepository,
                                 AuditoriaPort auditoria,
                                 Clock clock) {
        this.contratoRepository = contratoRepository;
        this.ticketRepository = ticketRepository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ==================================================================
    // Contrato_Mantenimiento (Req 20.1)
    // ==================================================================

    /**
     * Registra un Contrato_Mantenimiento asociado a un Cliente con sus datos
     * obligatorios (Req 20.1): valida el tipo e interpreta su etiqueta, comprueba
     * que los tiempos del SLA sean estrictamente positivos, crea el contrato,
     * persiste y audita.
     *
     * @param comando datos del contrato a registrar.
     * @return el DTO del Contrato_Mantenimiento creado (activo).
     * @throws ReglaNegocioException si el tipo es desconocido o los tiempos del SLA
     *         no son positivos (422).
     */
    @Transactional
    public ContratoMantenimientoDto crearContrato(CrearContratoCommand comando) {
        String actor = actorActual();
        TipoContratoMantenimiento tipo = interpretarTipo(comando.tipo());
        ContratoMantenimiento contrato = ContratoMantenimiento.crear(
                comando.clienteId(), tipo,
                comando.slaRespuestaHoras(), comando.slaResolucionHoras(), actor);
        ContratoMantenimiento guardado = contratoRepository.save(contrato);
        auditarContrato(actor, "crear", guardado.getId(),
                "registrado Contrato_Mantenimiento '" + guardado.getTipo().valorBd()
                        + "' [cliente=" + guardado.getClienteId()
                        + ", sla_respuesta_horas=" + guardado.getSlaRespuestaHoras()
                        + ", sla_resolucion_horas=" + guardado.getSlaResolucionHoras() + "]");
        return ContratoMantenimientoDto.de(guardado);
    }

    /**
     * Consulta puntual de un Contrato_Mantenimiento del tenant (Req 23.3).
     *
     * @param contratoId identificador del contrato.
     * @return el DTO del Contrato_Mantenimiento.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ContratoMantenimientoDto consultarContrato(UUID contratoId) {
        String actor = actorActual();
        return ContratoMantenimientoDto.de(cargarContrato(contratoId, actor));
    }

    /**
     * Listado paginado de Contratos_Mantenimiento del tenant con filtro opcional por
     * Cliente (Req 20.1, 20.7). Un filtro nulo no restringe; sin coincidencias se
     * devuelve una pagina vacia con total 0.
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Contratos_Mantenimiento como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ContratoMantenimientoDto> listarContratos(UUID clienteId, Pageable pageable) {
        return contratoRepository.buscarConFiltros(clienteId, pageable)
                .map(ContratoMantenimientoDto::de);
    }

    // ==================================================================
    // Ticket_Servicio (Req 20.2–20.7)
    // ==================================================================

    /**
     * Genera un Ticket_Servicio en estado {@code abierto} (Req 20.2). El ticket
     * puede ser manual (sin contrato) o generado por mantenimiento preventivo (con
     * contrato); si trae contrato, se valida que exista en el tenant. Audita la
     * creacion (estado anterior {@code null}, nuevo {@code abierto}, Req 20.8).
     *
     * @param comando datos del ticket a generar.
     * @return el DTO del Ticket_Servicio creado (estado {@code abierto}).
     * @throws ReglaNegocioException si el origen es desconocido (422).
     * @throws RecursoNoEncontradoException si el contrato indicado no existe en el
     *         tenant (404, Req 23.3).
     */
    @Transactional
    public TicketServicioDto generarTicket(GenerarTicketCommand comando) {
        String actor = actorActual();
        OrigenTicket origen = interpretarOrigen(comando.origen());
        UUID contratoId = comando.contratoMantenimientoId();
        if (contratoId != null) {
            // El contrato debe existir en el tenant (Req 23.3).
            cargarContrato(contratoId, actor);
        }
        TicketServicio ticket = TicketServicio.abrir(
                contratoId, comando.clienteId(), origen, ahora(), actor);
        TicketServicio guardado = ticketRepository.save(ticket);
        auditarTicket(actor, "crear", guardado.getId(),
                "generado Ticket_Servicio en estado '" + guardado.getEstado().valorBd()
                        + "' [origen=" + guardado.getOrigen().valorBd()
                        + ", cliente=" + guardado.getClienteId()
                        + ", contrato=" + contratoId + "]",
                null, guardado.getEstado().valorBd());
        return TicketServicioDto.de(guardado);
    }

    /**
     * Asigna un Ticket_Servicio a un tecnico o a una Cuadrilla (Req 20.3): fija la
     * asignacion y aplica la transicion {@code abierto -> asignado}. Audita el
     * cambio de estado (Req 20.8).
     *
     * @param ticketId identificador del ticket.
     * @param comando  datos de la asignacion (tipo y destinatario).
     * @return el DTO del Ticket_Servicio ya asignado.
     * @throws RecursoNoEncontradoException si el ticket no es accesible (404).
     * @throws ReglaNegocioException si el tipo de asignacion es desconocido (422).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si el
     *         ticket no esta en {@code abierto} (409, Req 20.5).
     */
    @Transactional
    public TicketServicioDto asignarTicket(UUID ticketId, AsignarTicketCommand comando) {
        String actor = actorActual();
        AsignadoTipo tipo = interpretarAsignadoTipo(comando.asignadoTipo());
        TicketServicio ticket = cargarTicket(ticketId, actor);
        EstadoTicketServicio anterior = ticket.getEstado();
        ticket.asignar(tipo, comando.asignadoId(), actor);
        TicketServicio guardado = ticketRepository.save(ticket);
        auditarTicket(actor, "cambiar_estado", guardado.getId(),
                "asignado Ticket_Servicio a " + tipo.valorBd() + " [id=" + comando.asignadoId()
                        + "]; cambio de estado '" + anterior.valorBd() + "' -> '"
                        + guardado.getEstado().valorBd() + "'",
                anterior.valorBd(), guardado.getEstado().valorBd());
        return TicketServicioDto.de(guardado);
    }

    /**
     * Cambia el estado de un Ticket_Servicio aplicando la maquina de estados pura
     * (Req 20.4, 20.5). Al transitar a {@code resuelto} evalua el cumplimiento del
     * SLA contra el contrato asociado (Req 20.6) y fija el instante de resolucion.
     * Audita el estado anterior y el nuevo (Req 20.8).
     *
     * @param ticketId    identificador del ticket.
     * @param nuevoEstado etiqueta del estado destino; obligatoria.
     * @return el DTO del Ticket_Servicio con su nuevo estado.
     * @throws RecursoNoEncontradoException si el ticket no es accesible (404).
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la
     *         transicion no esta permitida (409, Req 20.5).
     */
    @Transactional
    public TicketServicioDto cambiarEstadoTicket(UUID ticketId, String nuevoEstado) {
        String actor = actorActual();
        EstadoTicketServicio destino = interpretarEstado(nuevoEstado);
        TicketServicio ticket = cargarTicket(ticketId, actor);
        EstadoTicketServicio anterior = ticket.getEstado();

        if (destino == EstadoTicketServicio.RESUELTO) {
            resolverConSla(ticket, actor);
        } else {
            ticket.cambiarEstado(destino, actor);
        }

        TicketServicio guardado = ticketRepository.save(ticket);
        auditarTicket(actor, "cambiar_estado", guardado.getId(),
                "cambio de estado '" + anterior.valorBd() + "' -> '" + destino.valorBd() + "'",
                anterior.valorBd(), destino.valorBd());
        return TicketServicioDto.de(guardado);
    }

    /**
     * Consulta puntual de un Ticket_Servicio del tenant (Req 23.3).
     *
     * @param ticketId identificador del ticket.
     * @return el DTO del Ticket_Servicio.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public TicketServicioDto consultarTicket(UUID ticketId) {
        String actor = actorActual();
        return TicketServicioDto.de(cargarTicket(ticketId, actor));
    }

    /**
     * Listado paginado de Tickets_Servicio del tenant con filtros opcionales por
     * estado, por Cliente y por vencimiento del SLA (Req 20.7). Un filtro nulo no
     * restringe; sin coincidencias se devuelve una pagina vacia con total 0.
     *
     * <p>Cuando {@code slaVencido} es {@code true} se delega en la consulta de
     * vencimiento del repositorio (tickets no resueltos ni cerrados, con contrato,
     * cuyo tiempo transcurrido supera el SLA de resolucion). En caso contrario se
     * usa el filtro simple por estado y Cliente.</p>
     *
     * @param estado     etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param clienteId  Cliente a filtrar; {@code null} no filtra.
     * @param slaVencido {@code true} filtra los vencidos; {@code null}/{@code false}
     *                   no restringe.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de Tickets_Servicio como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<TicketServicioDto> listarTickets(String estado, UUID clienteId,
                                                 Boolean slaVencido, Pageable pageable) {
        EstadoTicketServicio filtroEstado =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        if (Boolean.TRUE.equals(slaVencido)) {
            String etiquetaEstado = (filtroEstado == null) ? null : filtroEstado.valorBd();
            return ticketRepository.buscarVencidos(etiquetaEstado, clienteId, ahora(), pageable)
                    .map(TicketServicioDto::de);
        }
        return ticketRepository.buscarConFiltros(filtroEstado, clienteId, pageable)
                .map(TicketServicioDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Resuelve el ticket evaluando el cumplimiento del SLA contra su contrato
     * (Req 20.6). Si el ticket no tiene contrato, las banderas quedan {@code null}
     * (sin SLA que evaluar). La transicion {@code en_proceso -> resuelto} la aplica
     * {@link TicketServicio#marcarResuelto}, que rechaza con 409 cualquier estado de
     * partida no valido.
     */
    private void resolverConSla(TicketServicio ticket, String actor) {
        Instant ahora = ahora();
        Boolean respuestaCumplida = null;
        Boolean resolucionCumplida = null;
        UUID contratoId = ticket.getContratoMantenimientoId();
        if (contratoId != null) {
            ContratoMantenimiento contrato = cargarContrato(contratoId, actor);
            long horasTranscurridas =
                    Duration.between(ticket.getAbiertoEn(), ahora).toHours();
            respuestaCumplida = horasTranscurridas <= contrato.getSlaRespuestaHoras();
            resolucionCumplida = horasTranscurridas <= contrato.getSlaResolucionHoras();
        }
        ticket.marcarResuelto(ahora, respuestaCumplida, resolucionCumplida, actor);
    }

    private ContratoMantenimiento cargarContrato(UUID contratoId, String actor) {
        if (contratoId == null) {
            throw new RecursoNoEncontradoException(
                    "No se encontro el Contrato_Mantenimiento solicitado.");
        }
        return contratoRepository.findById(contratoId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CONTRATO, contratoId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Contrato_Mantenimiento solicitado.");
                });
    }

    private TicketServicio cargarTicket(UUID ticketId, String actor) {
        if (ticketId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Ticket_Servicio solicitado.");
        }
        return ticketRepository.findById(ticketId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_TICKET, ticketId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro el Ticket_Servicio solicitado.");
                });
    }

    private TipoContratoMantenimiento interpretarTipo(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El tipo del Contrato_Mantenimiento es obligatorio.");
        }
        try {
            return TipoContratoMantenimiento.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException(
                    "Tipo de Contrato_Mantenimiento desconocido: " + etiqueta);
        }
    }

    private OrigenTicket interpretarOrigen(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El origen del Ticket_Servicio es obligatorio.");
        }
        try {
            return OrigenTicket.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Origen de Ticket_Servicio desconocido: " + etiqueta);
        }
    }

    private AsignadoTipo interpretarAsignadoTipo(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El tipo de asignacion es obligatorio.");
        }
        try {
            return AsignadoTipo.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Tipo de asignacion desconocido: " + etiqueta);
        }
    }

    private EstadoTicketServicio interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        try {
            return EstadoTicketServicio.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Ticket_Servicio desconocido: " + etiqueta);
        }
    }

    private void auditarContrato(String actor, String accion, UUID contratoId, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_CONTRATO,
                detalle + " [id=" + contratoId + "]", null, null));
    }

    private void auditarTicket(String actor, String accion, UUID ticketId, String detalle,
                               String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_TICKET,
                detalle + " [id=" + ticketId + "]", valorAnterior, valorNuevo));
    }

    private void auditarAccesoCruzado(String actor, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", recurso,
                "intento de acceso a " + recurso + " no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private Instant ahora() {
        return clock.instant();
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}
