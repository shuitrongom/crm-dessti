package com.dessti.crm.social.domain;

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
 * Entidad JPA y raiz del agregado {@code conversacion}: el hilo unico de
 * intercambio de {@link MensajeSocial} entre la Empresa y un remitente en una
 * {@link CuentaCanalSocial}, mapeada sobre la tabla {@code conversacion} de la
 * migracion V41 (Req 64.5, 64.10, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}.
 * El mapeo de columnas coincide <em>exactamente</em> con V41.</p>
 *
 * <h2>Ventana_Servicio (Req 64.6, 64.7; Property 37)</h2>
 * <p>{@link #ultimoEntranteUtc} guarda el instante del ultimo Mensaje_Social
 * entrante y gobierna la Ventana_Servicio. {@link #registrarEntrante(Instant)} lo
 * actualiza. La decision de si un envio esta permitido la toma la funcion pura
 * {@link GuardaVentanaServicio}, a la que la aplicacion pasa este instante.</p>
 *
 * <h2>Handover (Req 64.10)</h2>
 * <p>El {@link #estado} (abierta/asignada/cerrada) se gobierna con la maquina de
 * estados pura de {@link EstadoConversacion}. {@link #asignar(UUID, String)}
 * transfiere la Conversacion a un Usuario; {@link #cerrar(String)} la concluye.</p>
 */
@Entity
@Table(name = "conversacion")
public class Conversacion extends TenantScopedEntity {

    /** Longitud maxima del remitente externo (coincide con VARCHAR(120) de V41). */
    static final int MAX_REMITENTE = 120;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cuenta de canal a la que pertenece la Conversacion; inmutable (Req 64.5). */
    @Column(name = "cuenta_canal_social_id", nullable = false, updatable = false)
    private UUID cuentaCanalSocialId;

    /** Canal_Social de la Conversacion; inmutable (Req 64.5). */
    @Convert(converter = CanalSocialConverter.class)
    @Column(name = "canal", nullable = false, length = 12, updatable = false)
    private CanalSocial canal;

    /** Identificador del remitente en el canal; inmutable (Req 64.5). */
    @Column(name = "remitente_externo", nullable = false, length = MAX_REMITENTE, updatable = false)
    private String remitenteExterno;

    /** Cliente asociado, si se resolvio; {@code null} en caso contrario (Req 64.4). */
    @Column(name = "cliente_id")
    private UUID clienteId;

    /** Contacto asociado, si se resolvio o creo (captura de leads); {@code null} en caso contrario. */
    @Column(name = "contacto_id")
    private UUID contactoId;

    /** Estado de la Conversacion (abierta/asignada/cerrada) (Req 64.10). */
    @Convert(converter = EstadoConversacionConverter.class)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoConversacion estado;

    /** Usuario asignado (handover); {@code null} mientras esta sin asignar (Req 64.10). */
    @Column(name = "asignado_a")
    private UUID asignadoA;

    /** Instante del ultimo Mensaje_Social entrante (UTC); gobierna la Ventana_Servicio. */
    @Column(name = "ultimo_entrante_utc")
    private Instant ultimoEntranteUtc;

    protected Conversacion() {
        // Requerido por JPA.
    }

    /**
     * Abre una Conversacion nueva (estado {@link EstadoConversacion#ABIERTA}) para
     * un remitente en una cuenta de canal (Req 64.5). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4). El
     * {@link #ultimoEntranteUtc} arranca en {@code null} (aun sin entrantes).
     *
     * @param cuentaCanalSocialId cuenta de canal; obligatorio.
     * @param canal               Canal_Social; obligatorio.
     * @param remitenteExterno    identificador del remitente en el canal; obligatorio.
     * @param clienteId           Cliente asociado; opcional.
     * @param contactoId          Contacto asociado; opcional.
     * @param actor               identificador de origen (auditoria).
     * @return la Conversacion lista para persistir, en estado {@code abierta}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static Conversacion abrir(UUID cuentaCanalSocialId, CanalSocial canal,
                                     String remitenteExterno, UUID clienteId, UUID contactoId,
                                     String actor) {
        if (cuentaCanalSocialId == null) {
            throw new ReglaNegocioException("La Conversacion debe asociarse a una Cuenta_Canal_Social.");
        }
        if (canal == null) {
            throw new ReglaNegocioException("La Conversacion debe indicar el Canal_Social.");
        }
        if (remitenteExterno == null || remitenteExterno.isBlank()) {
            throw new ReglaNegocioException("La Conversacion debe indicar el remitente.");
        }
        String remitente = remitenteExterno.strip();
        if (remitente.length() > MAX_REMITENTE) {
            throw new ReglaNegocioException(
                    "El remitente de la Conversacion no puede exceder " + MAX_REMITENTE + " caracteres.");
        }

        Conversacion conversacion = new Conversacion();
        conversacion.id = UUID.randomUUID();
        conversacion.cuentaCanalSocialId = cuentaCanalSocialId;
        conversacion.canal = canal;
        conversacion.remitenteExterno = remitente;
        conversacion.clienteId = clienteId;
        conversacion.contactoId = contactoId;
        conversacion.estado = EstadoConversacion.ABIERTA;
        conversacion.setCreatedBy(actor);
        conversacion.setUpdatedBy(actor);
        return conversacion;
    }

    /**
     * Registra la llegada de un Mensaje_Social entrante actualizando el
     * {@link #ultimoEntranteUtc} que gobierna la Ventana_Servicio (Req 64.6). Solo
     * avanza el instante (nunca lo retrocede ante eventos fuera de orden).
     *
     * @param recibidoEn instante de recepcion del entrante (UTC); obligatorio.
     * @param actor      identificador de origen, para {@code updated_by}.
     * @throws ReglaNegocioException si {@code recibidoEn} es nulo (422).
     */
    public void registrarEntrante(Instant recibidoEn, String actor) {
        if (recibidoEn == null) {
            throw new ReglaNegocioException("La marca temporal del entrante es obligatoria.");
        }
        if (this.ultimoEntranteUtc == null || recibidoEn.isAfter(this.ultimoEntranteUtc)) {
            this.ultimoEntranteUtc = recibidoEn;
        }
        this.setUpdatedBy(actor);
    }

    /**
     * Asigna/transfiere la Conversacion a un Usuario (handover, Req 64.10),
     * transitando a {@link EstadoConversacion#ASIGNADA} mediante la maquina de
     * estados pura. Permite la reasignacion ({@code asignada -> asignada}).
     *
     * @param usuarioId Usuario responsable; obligatorio.
     * @param actor     identificador de quien asigna, para {@code updated_by}.
     * @throws ReglaNegocioException       si {@code usuarioId} es nulo (422).
     * @throws TransicionInvalidaException si la Conversacion esta cerrada (409).
     */
    public void asignar(UUID usuarioId, String actor) {
        if (usuarioId == null) {
            throw new ReglaNegocioException("El handover debe indicar el Usuario asignado.");
        }
        if (!this.estado.puedeTransicionarA(EstadoConversacion.ASIGNADA)) {
            throw new TransicionInvalidaException(
                    "La Conversacion esta cerrada; no admite asignacion.");
        }
        this.estado = EstadoConversacion.ASIGNADA;
        this.asignadoA = usuarioId;
        this.setUpdatedBy(actor);
    }

    /**
     * Cierra la Conversacion (Req 64.10), transitando a
     * {@link EstadoConversacion#CERRADA}. Conserva el historial de mensajes.
     *
     * @param actor identificador de quien cierra, para {@code updated_by}.
     * @throws TransicionInvalidaException si la Conversacion ya esta cerrada (409).
     */
    public void cerrar(String actor) {
        if (!this.estado.puedeTransicionarA(EstadoConversacion.CERRADA)) {
            throw new TransicionInvalidaException(
                    "La Conversacion ya esta cerrada; no admite un nuevo cierre.");
        }
        this.estado = EstadoConversacion.CERRADA;
        this.setUpdatedBy(actor);
    }

    /**
     * Vincula la Conversacion a un Cliente y/o Contacto existentes o recien creados
     * (asociacion/captura de leads, Req 64.4). No modifica el estado.
     *
     * @param clienteId  Cliente a vincular; {@code null} no modifica el actual.
     * @param contactoId Contacto a vincular; {@code null} no modifica el actual.
     * @param actor      identificador de quien vincula, para {@code updated_by}.
     */
    public void vincular(UUID clienteId, UUID contactoId, String actor) {
        if (clienteId != null) {
            this.clienteId = clienteId;
        }
        if (contactoId != null) {
            this.contactoId = contactoId;
        }
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si la Conversacion esta cerrada (estado final).
     *
     * @return {@code true} si el estado es {@link EstadoConversacion#CERRADA}.
     */
    public boolean estaCerrada() {
        return estado == EstadoConversacion.CERRADA;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCuentaCanalSocialId() {
        return cuentaCanalSocialId;
    }

    public CanalSocial getCanal() {
        return canal;
    }

    public String getRemitenteExterno() {
        return remitenteExterno;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public UUID getContactoId() {
        return contactoId;
    }

    public EstadoConversacion getEstado() {
        return estado;
    }

    public UUID getAsignadoA() {
        return asignadoA;
    }

    public Instant getUltimoEntranteUtc() {
        return ultimoEntranteUtc;
    }
}
