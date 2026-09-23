package com.dessti.crm.social.domain;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code mensaje_social}: un Mensaje_Social individual (entrante o
 * saliente) de una {@link Conversacion}, mapeada sobre la tabla
 * {@code mensaje_social} de la migracion V41 (Req 64.4, 64.11, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}.
 * El mapeo de columnas coincide <em>exactamente</em> con V41.</p>
 *
 * <h2>Estado de entrega (Req 64.11)</h2>
 * <p>En los mensajes salientes, {@link #estadoEntrega} refleja el estado devuelto
 * por el proveedor via el adaptador de {@code MensajeriaSocialPort}
 * (enviado/entregado/leido/fallido) y puede actualizarse con
 * {@link #actualizarEstadoEntrega(EstadoEntrega, String)}. En los entrantes es
 * {@code null}.</p>
 */
@Entity
@Table(name = "mensaje_social")
public class MensajeSocial extends TenantScopedEntity {

    /** Longitud maxima del identificador externo (coincide con VARCHAR(120) de V41). */
    static final int MAX_EXTERNO_ID = 120;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Conversacion a la que pertenece; inmutable (Req 64.4). */
    @Column(name = "conversacion_id", nullable = false, updatable = false)
    private UUID conversacionId;

    /** Sentido del mensaje (entrante/saliente); inmutable (Req 64.4). */
    @Convert(converter = DireccionMensajeConverter.class)
    @Column(name = "direccion", nullable = false, length = 8, updatable = false)
    private DireccionMensaje direccion;

    /** Tipo del mensaje (texto/plantilla/interactivo); inmutable (Req 64.7, 64.11). */
    @Convert(converter = TipoMensajeConverter.class)
    @Column(name = "tipo", nullable = false, length = 12, updatable = false)
    private TipoMensaje tipo;

    /** Contenido del mensaje; no vacio; inmutable. */
    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String contenido;

    /** Marca de marketing (sujeta a la guarda de Opt_In, Req 64.8); inmutable. */
    @Column(name = "es_marketing", nullable = false, updatable = false)
    private boolean esMarketing;

    /** Estado de entrega (salientes); {@code null} en entrantes (Req 64.11). */
    @Convert(converter = EstadoEntregaConverter.class)
    @Column(name = "estado_entrega", length = 12)
    private EstadoEntrega estadoEntrega;

    /** Identificador externo del proveedor (id del mensaje en Meta); {@code null} si no aplica. */
    @Column(name = "externo_id", length = MAX_EXTERNO_ID)
    private String externoId;

    /** Instante de envio (UTC) para salientes; {@code null} en entrantes. */
    @Column(name = "enviado_en")
    private Instant enviadoEn;

    /** Instante de recepcion (UTC) para entrantes; {@code null} en salientes. */
    @Column(name = "recibido_en")
    private Instant recibidoEn;

    protected MensajeSocial() {
        // Requerido por JPA.
    }

    /**
     * Crea un Mensaje_Social <strong>entrante</strong> (del Cliente hacia la
     * Empresa) (Req 64.4). Los entrantes se registran siempre como
     * {@link TipoMensaje#TEXTO} de servicio (no marketing) y con
     * {@code estadoEntrega} nulo.
     *
     * @param conversacionId Conversacion a la que pertenece; obligatorio.
     * @param contenido      contenido; obligatorio (no vacio).
     * @param externoId      id del mensaje en el proveedor; opcional.
     * @param recibidoEn     instante de recepcion (UTC); obligatorio.
     * @param actor          identificador de origen (auditoria).
     * @return el Mensaje_Social entrante listo para persistir.
     * @throws ReglaNegocioException si el contenido falta o la marca temporal es nula (422).
     */
    public static MensajeSocial entrante(UUID conversacionId, String contenido, String externoId,
                                         Instant recibidoEn, String actor) {
        if (conversacionId == null) {
            throw new ReglaNegocioException("El Mensaje_Social debe asociarse a una Conversacion.");
        }
        if (contenido == null || contenido.isBlank()) {
            throw new ReglaNegocioException("El Mensaje_Social debe indicar el contenido.");
        }
        if (recibidoEn == null) {
            throw new ReglaNegocioException("El Mensaje_Social entrante debe indicar la marca temporal UTC.");
        }
        MensajeSocial mensaje = new MensajeSocial();
        mensaje.id = UUID.randomUUID();
        mensaje.conversacionId = conversacionId;
        mensaje.direccion = DireccionMensaje.ENTRANTE;
        mensaje.tipo = TipoMensaje.TEXTO;
        mensaje.contenido = contenido.strip();
        mensaje.esMarketing = false;
        mensaje.estadoEntrega = null;
        mensaje.externoId = normalizarExternoId(externoId);
        mensaje.recibidoEn = recibidoEn;
        mensaje.setCreatedBy(actor);
        mensaje.setUpdatedBy(actor);
        return mensaje;
    }

    /**
     * Crea un Mensaje_Social <strong>saliente</strong> (de la Empresa hacia el
     * Cliente) con el tipo indicado (texto/plantilla/interactivo) (Req 64.6, 64.7,
     * 64.11). Las guardas de Ventana_Servicio y de Opt_In las aplica la capa de
     * aplicacion <em>antes</em> de crear el mensaje.
     *
     * @param conversacionId Conversacion a la que pertenece; obligatorio.
     * @param tipo           tipo del mensaje; obligatorio.
     * @param contenido      contenido; obligatorio (no vacio).
     * @param esMarketing    {@code true} si es un mensaje de marketing (Req 64.8).
     * @param enviadoEn      instante de envio (UTC); obligatorio.
     * @param actor          identificador de quien envia (auditoria).
     * @return el Mensaje_Social saliente listo para persistir (sin estado de entrega aun).
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static MensajeSocial saliente(UUID conversacionId, TipoMensaje tipo, String contenido,
                                         boolean esMarketing, Instant enviadoEn, String actor) {
        if (conversacionId == null) {
            throw new ReglaNegocioException("El Mensaje_Social debe asociarse a una Conversacion.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("El Mensaje_Social saliente debe indicar el tipo.");
        }
        if (contenido == null || contenido.isBlank()) {
            throw new ReglaNegocioException("El Mensaje_Social debe indicar el contenido.");
        }
        if (enviadoEn == null) {
            throw new ReglaNegocioException("El Mensaje_Social saliente debe indicar la marca temporal UTC.");
        }
        MensajeSocial mensaje = new MensajeSocial();
        mensaje.id = UUID.randomUUID();
        mensaje.conversacionId = conversacionId;
        mensaje.direccion = DireccionMensaje.SALIENTE;
        mensaje.tipo = tipo;
        mensaje.contenido = contenido.strip();
        mensaje.esMarketing = esMarketing;
        mensaje.estadoEntrega = null;
        mensaje.enviadoEn = enviadoEn;
        mensaje.setCreatedBy(actor);
        mensaje.setUpdatedBy(actor);
        return mensaje;
    }

    /**
     * Fija el resultado del envio saliente: el {@link #externoId id externo} del
     * proveedor y el {@link #estadoEntrega estado de entrega} inicial (Req 64.11).
     *
     * @param externoId     id del mensaje en el proveedor; opcional.
     * @param estadoEntrega estado de entrega inicial; obligatorio.
     * @param actor         identificador de quien actualiza, para {@code updated_by}.
     */
    public void registrarResultadoEnvio(String externoId, EstadoEntrega estadoEntrega, String actor) {
        this.externoId = normalizarExternoId(externoId);
        this.estadoEntrega = estadoEntrega;
        this.setUpdatedBy(actor);
    }

    /**
     * Actualiza el estado de entrega del mensaje saliente (por ejemplo, al recibir
     * un callback del proveedor) (Req 64.11).
     *
     * @param estadoEntrega nuevo estado de entrega; obligatorio.
     * @param actor         identificador de quien actualiza, para {@code updated_by}.
     */
    public void actualizarEstadoEntrega(EstadoEntrega estadoEntrega, String actor) {
        this.estadoEntrega = estadoEntrega;
        this.setUpdatedBy(actor);
    }

    private static String normalizarExternoId(String externoId) {
        if (externoId == null || externoId.isBlank()) {
            return null;
        }
        String limpio = externoId.strip();
        return (limpio.length() > MAX_EXTERNO_ID) ? limpio.substring(0, MAX_EXTERNO_ID) : limpio;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversacionId() {
        return conversacionId;
    }

    public DireccionMensaje getDireccion() {
        return direccion;
    }

    public TipoMensaje getTipo() {
        return tipo;
    }

    public String getContenido() {
        return contenido;
    }

    public boolean isEsMarketing() {
        return esMarketing;
    }

    public EstadoEntrega getEstadoEntrega() {
        return estadoEntrega;
    }

    public String getExternoId() {
        return externoId;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public Instant getRecibidoEn() {
        return recibidoEn;
    }
}
