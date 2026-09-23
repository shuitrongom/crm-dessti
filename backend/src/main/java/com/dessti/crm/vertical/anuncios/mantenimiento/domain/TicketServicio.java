package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code ticket_servicio}: una incidencia/orden de
 * servicio con maquina de estados y evaluacion de cumplimiento del SLA, mapeada
 * sobre la tabla {@code ticket_servicio} de la migracion V27 (Req 20.2–20.6, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V27.</p>
 *
 * <h2>Reglas de dominio (Req 20.2–20.6)</h2>
 * <ul>
 *   <li>{@link #abrir(UUID, UUID, OrigenTicket, Instant, String)} crea el ticket en
 *       estado {@link EstadoTicketServicio#ABIERTO} (Req 20.2), fijando el instante
 *       de apertura {@code abierto_en} (base del calculo del SLA). El contrato es
 *       opcional (un ticket manual puede no tenerlo, V27 DECISION 2); el Cliente es
 *       obligatorio (denormalizado para el filtro del Req 20.7).</li>
 *   <li>{@link #asignar(AsignadoTipo, UUID, String)} registra la asignacion a un
 *       tecnico o Cuadrilla (Req 20.3) y aplica la transicion
 *       {@code abierto -> asignado} de la maquina de estados pura.</li>
 *   <li>{@link #cambiarEstado(EstadoTicketServicio, String)} aplica la maquina de
 *       estados pura (Req 20.4, 20.5); toda transicion no permitida se rechaza con
 *       {@link TransicionInvalidaException} (409) conservando el estado actual.</li>
 *   <li>{@link #marcarResuelto(Instant, Boolean, Boolean, String)} lleva el ticket a
 *       {@link EstadoTicketServicio#RESUELTO} fijando el instante de resolucion y
 *       las banderas de cumplimiento del SLA (Req 20.6). El <em>calculo</em> del
 *       cumplimiento vive en la capa de aplicacion ({@code ServicioMantenimiento}),
 *       pues requiere el Contrato_Mantenimiento asociado y el {@code Clock}; el
 *       dominio solo persiste el resultado. Este reparto es analogo al de las
 *       precondiciones de la Orden_Fabricacion, que viven en el servicio.</li>
 * </ul>
 */
@Entity
@Table(name = "ticket_servicio")
public class TicketServicio extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Contrato_Mantenimiento de SLA asociado; puede ser {@code null} en tickets
     * manuales (V27 DECISION 2). La evaluacion del SLA (Req 20.6) solo aplica si
     * existe. Inmutable tras la apertura.
     */
    @Column(name = "contrato_mantenimiento_id", updatable = false)
    private UUID contratoMantenimientoId;

    /**
     * Cliente del ticket, denormalizado para el filtro del listado (Req 20.7).
     * Inmutable; se fija al abrir el ticket.
     */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /** Origen del ticket (Req 20.2): {@code manual} o {@code preventivo}. */
    @Convert(converter = OrigenTicketConverter.class)
    @Column(name = "origen", nullable = false, updatable = false)
    private OrigenTicket origen;

    /** Estado; se persiste como etiqueta ASCII (Req 20.2, 20.4). */
    @Convert(converter = EstadoTicketServicioConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoTicketServicio estado;

    /** Tipo de destinatario de la asignacion (Req 20.3); {@code null} sin asignar. */
    @Convert(converter = AsignadoTipoConverter.class)
    @Column(name = "asignado_tipo")
    private AsignadoTipo asignadoTipo;

    /**
     * Referencia debil (UUID sin FK) al destinatario de la asignacion (Req 20.3,
     * V27 DECISION 3); {@code null} mientras el ticket no este asignado.
     */
    @Column(name = "asignado_id")
    private UUID asignadoId;

    /** Marca UTC de apertura del ticket (Req 20.6): base del calculo del SLA. */
    @Column(name = "abierto_en", nullable = false, updatable = false)
    private Instant abiertoEn;

    /** Marca UTC de resolucion del ticket (Req 20.6); {@code null} hasta resolver. */
    @Column(name = "resuelto_en")
    private Instant resueltoEn;

    /**
     * Cumplimiento del tiempo de respuesta del SLA (Req 20.6); {@code null} hasta la
     * resolucion o si el ticket no tiene contrato.
     */
    @Column(name = "sla_respuesta_cumplido")
    private Boolean slaRespuestaCumplido;

    /**
     * Cumplimiento del tiempo de resolucion del SLA (Req 20.6); {@code null} hasta
     * la resolucion o si el ticket no tiene contrato.
     */
    @Column(name = "sla_resolucion_cumplido")
    private Boolean slaResolucionCumplido;

    protected TicketServicio() {
        // Requerido por JPA.
    }

    /**
     * Abre un Ticket_Servicio en estado {@link EstadoTicketServicio#ABIERTO}
     * (Req 20.2), fijando el instante de apertura {@code abiertoEn} (base del
     * calculo del SLA, Req 20.6). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param contratoMantenimientoId Contrato de SLA asociado; puede ser
     *                                {@code null} en tickets manuales (V27 DECISION 2).
     * @param clienteId               Cliente del ticket (para el filtro del Req 20.7);
     *                                obligatorio.
     * @param origen                  origen del ticket ({@code manual}/
     *                                {@code preventivo}); obligatorio.
     * @param abiertoEn               instante UTC de apertura (del {@code Clock}
     *                                inyectado); obligatorio.
     * @param actor                   identificador de quien abre, para
     *                                {@code created_by}/{@code updated_by}.
     * @return el Ticket_Servicio listo para persistir, en estado {@code abierto}.
     * @throws ReglaNegocioException si falta el Cliente, el origen o el instante de
     *         apertura (422).
     */
    public static TicketServicio abrir(UUID contratoMantenimientoId, UUID clienteId,
                                       OrigenTicket origen, Instant abiertoEn, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException(
                    "El Ticket_Servicio debe registrar el Cliente asociado.");
        }
        if (origen == null) {
            throw new ReglaNegocioException("El origen del Ticket_Servicio es obligatorio.");
        }
        if (abiertoEn == null) {
            throw new ReglaNegocioException(
                    "El instante de apertura del Ticket_Servicio es obligatorio.");
        }
        TicketServicio ticket = new TicketServicio();
        ticket.id = UUID.randomUUID();
        ticket.contratoMantenimientoId = contratoMantenimientoId;
        ticket.clienteId = clienteId;
        ticket.origen = origen;
        ticket.estado = EstadoTicketServicio.ABIERTO;
        ticket.abiertoEn = abiertoEn;
        ticket.setCreatedBy(actor);
        ticket.setUpdatedBy(actor);
        return ticket;
    }

    /**
     * Registra la asignacion del ticket a un tecnico o a una Cuadrilla (Req 20.3) y
     * aplica la transicion {@code abierto -> asignado} de la maquina de estados
     * pura. Los datos de asignacion ({@code asignadoTipo}/{@code asignadoId}) se
     * fijan aqui; la validez de la transicion la gobierna
     * {@link #cambiarEstado(EstadoTicketServicio, String)}.
     *
     * @param asignadoTipo tipo de destinatario ({@code tecnico}/{@code cuadrilla});
     *                     obligatorio.
     * @param asignadoId   referencia (debil) al destinatario; obligatorio.
     * @param actor        identificador de quien asigna, para {@code updated_by}.
     * @throws ReglaNegocioException       si falta el tipo o el destinatario (422).
     * @throws TransicionInvalidaException si el ticket no esta en {@code abierto}
     *                                     (409, Req 20.5).
     */
    public void asignar(AsignadoTipo asignadoTipo, UUID asignadoId, String actor) {
        if (asignadoTipo == null) {
            throw new ReglaNegocioException("El tipo de asignacion es obligatorio.");
        }
        if (asignadoId == null) {
            throw new ReglaNegocioException("El destinatario de la asignacion es obligatorio.");
        }
        cambiarEstado(EstadoTicketServicio.ASIGNADO, actor);
        this.asignadoTipo = asignadoTipo;
        this.asignadoId = asignadoId;
    }

    /**
     * Cambia el estado del Ticket_Servicio aplicando la maquina de estados pura
     * (Req 20.4, 20.5). Solo permite las transiciones definidas; toda transicion no
     * permitida —incluida cualquiera que parta del estado final— se rechaza con
     * {@link TransicionInvalidaException} (409) y el estado actual se conserva sin
     * modificarlo (Req 20.5).
     *
     * <p>Para la transicion a {@link EstadoTicketServicio#RESUELTO} use
     * {@link #marcarResuelto(Instant, Boolean, Boolean, String)}, que ademas fija el
     * instante de resolucion y las banderas del SLA (Req 20.6).</p>
     *
     * @param nuevoEstado estado destino; obligatorio.
     * @param actor       identificador de quien realiza el cambio, para
     *                    {@code updated_by}.
     * @throws ReglaNegocioException       si {@code nuevoEstado} es nulo (422).
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoTicketServicio nuevoEstado, String actor) {
        if (nuevoEstado == null) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(nuevoEstado)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + nuevoEstado.valorBd() + "'.");
        }
        this.estado = nuevoEstado;
        this.setUpdatedBy(actor);
    }

    /**
     * Lleva el ticket a {@link EstadoTicketServicio#RESUELTO} (Req 20.6) aplicando
     * la maquina de estados pura y registrando el instante de resolucion y el
     * resultado de la evaluacion del SLA. El <em>calculo</em> de las banderas lo
     * realiza la capa de aplicacion (necesita el contrato y el {@code Clock}); si el
     * ticket no tiene contrato, las banderas se pasan {@code null} y quedan sin
     * evaluar (V27 DECISION 2).
     *
     * @param instanteResolucion   instante UTC de resolucion (del {@code Clock});
     *                             obligatorio.
     * @param slaRespuestaCumplido cumplimiento del tiempo de respuesta, o
     *                             {@code null} si no hay contrato.
     * @param slaResolucionCumplido cumplimiento del tiempo de resolucion, o
     *                             {@code null} si no hay contrato.
     * @param actor                identificador de quien resuelve, para
     *                             {@code updated_by}.
     * @throws ReglaNegocioException       si el instante de resolucion es nulo (422).
     * @throws TransicionInvalidaException si el ticket no esta en {@code en_proceso}
     *                                     (409, Req 20.5).
     */
    public void marcarResuelto(Instant instanteResolucion, Boolean slaRespuestaCumplido,
                               Boolean slaResolucionCumplido, String actor) {
        if (instanteResolucion == null) {
            throw new ReglaNegocioException(
                    "El instante de resolucion del Ticket_Servicio es obligatorio.");
        }
        cambiarEstado(EstadoTicketServicio.RESUELTO, actor);
        this.resueltoEn = instanteResolucion;
        this.slaRespuestaCumplido = slaRespuestaCumplido;
        this.slaResolucionCumplido = slaResolucionCumplido;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContratoMantenimientoId() {
        return contratoMantenimientoId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public OrigenTicket getOrigen() {
        return origen;
    }

    public EstadoTicketServicio getEstado() {
        return estado;
    }

    public AsignadoTipo getAsignadoTipo() {
        return asignadoTipo;
    }

    public UUID getAsignadoId() {
        return asignadoId;
    }

    public Instant getAbiertoEn() {
        return abiertoEn;
    }

    public Instant getResueltoEn() {
        return resueltoEn;
    }

    public Boolean getSlaRespuestaCumplido() {
        return slaRespuestaCumplido;
    }

    public Boolean getSlaResolucionCumplido() {
        return slaResolucionCumplido;
    }
}
