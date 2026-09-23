package com.dessti.crm.notificaciones.domain;

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
 * Entidad JPA y raiz del agregado {@code notificacion}: una Notificacion generada
 * ante un evento relevante (Req 46.1) y destinada a un destinatario por correo o por
 * un Canal_Social. Mapeada sobre la tabla {@code notificacion} de la migracion V42
 * (Req 46, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde la
 * peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las marcas
 * de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V42.</p>
 *
 * <h2>Contenido minimo, sin datos sensibles (Req 46.4)</h2>
 * <p>El {@link #contenido} y el {@link #asunto} deben limitarse a lo necesario para
 * el aviso, sin exponer datos sensibles innecesarios. La minimizacion es
 * responsabilidad del <em>llamador</em> que solicita la Notificacion (los modulos
 * productores); esta entidad solo acota longitudes maximas (asunto &le; 200,
 * contenido &le; 2000) coherentes con V42.</p>
 *
 * <h2>Ciclo de vida (Req 46.1, 46.3, 46.7)</h2>
 * <ul>
 *   <li>{@link #generar} crea la Notificacion en estado
 *       {@link EstadoNotificacion#PENDIENTE}.</li>
 *   <li>{@link #marcarEnviada(Instant)} tras un envio exitoso (Req 46.3).</li>
 *   <li>{@link #marcarFallida()} tras agotar la politica de reintentos (Req 46.3).</li>
 *   <li>{@link #marcarOmitida(String)} cuando se omite el envio por falta de Opt_In
 *       vigente en un Canal_Social de marketing (Req 46.7).</li>
 * </ul>
 */
@Entity
@Table(name = "notificacion")
public class Notificacion extends TenantScopedEntity {

    /** Longitud maxima del asunto, coherente con V42 (VARCHAR(200)). */
    public static final int MAX_ASUNTO = 200;

    /** Longitud maxima del contenido, coherente con V42 (VARCHAR(2000)). */
    public static final int MAX_CONTENIDO = 2000;

    /** Longitud maxima del destinatario, coherente con V42 (VARCHAR(320)). */
    public static final int MAX_DESTINATARIO = 320;

    /** Longitud maxima del motivo de omision, coherente con V42 (VARCHAR(300)). */
    public static final int MAX_MOTIVO_OMISION = 300;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Evento de negocio que origino la Notificacion (Req 46.1). */
    @Convert(converter = TipoEventoNotificacionConverter.class)
    @Column(name = "evento_origen", nullable = false, updatable = false)
    private TipoEventoNotificacion eventoOrigen;

    /** Canal de entrega (Req 46.1, 46.6). */
    @Convert(converter = CanalNotificacionConverter.class)
    @Column(name = "canal", nullable = false, updatable = false)
    private CanalNotificacion canal;

    /**
     * Destinatario: correo, telefono o identificador social, segun el canal
     * (Req 46.1). No es un secreto; es el dato minimo de entrega.
     */
    @Column(name = "destinatario", nullable = false, updatable = false)
    private String destinatario;

    /** Asunto opcional (correo); &le; 200 (Req 46.4). */
    @Column(name = "asunto")
    private String asunto;

    /** Contenido minimo del aviso, sin datos sensibles; &le; 2000 (Req 46.4). */
    @Column(name = "contenido", nullable = false, updatable = false)
    private String contenido;

    /** Indica si la Notificacion es de marketing (relevante para el Opt_In, Req 46.7). */
    @Column(name = "es_marketing", nullable = false)
    private boolean esMarketing;

    /** Tipo del recurso de negocio referenciado por el evento (opcional). */
    @Column(name = "referencia_tipo", updatable = false)
    private String referenciaTipo;

    /** Identificador del recurso de negocio referenciado por el evento (opcional). */
    @Column(name = "referencia_id", updatable = false)
    private UUID referenciaId;

    /** Estado del ciclo de vida (Req 46.1, 46.3, 46.7). */
    @Convert(converter = EstadoNotificacionConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoNotificacion estado;

    /** Motivo de la omision cuando {@link EstadoNotificacion#OMITIDA} (Req 46.7). */
    @Column(name = "motivo_omision")
    private String motivoOmision;

    /** Instante de generacion (UTC). */
    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    /** Instante de envio exitoso (UTC); {@code null} mientras no se haya enviado. */
    @Column(name = "enviada_en")
    private Instant enviadaEn;

    protected Notificacion() {
        // Requerido por JPA.
    }

    /**
     * Genera una Notificacion en estado {@link EstadoNotificacion#PENDIENTE} ante un
     * evento relevante (Req 46.1). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param eventoOrigen    evento de negocio que la origina; obligatorio.
     * @param canal           canal de entrega; obligatorio.
     * @param destinatario    correo/telefono/id social; obligatorio, &le; 320.
     * @param asunto          asunto opcional (correo); &le; 200.
     * @param contenido       contenido minimo sin datos sensibles; obligatorio, &le; 2000
     *                        (Req 46.4).
     * @param esMarketing     si la Notificacion es de marketing (Req 46.7).
     * @param referenciaTipo  tipo del recurso de negocio referenciado; opcional.
     * @param referenciaId    identificador del recurso referenciado; opcional.
     * @param actor           identificador de quien la genera, para {@code created_by}/
     *                        {@code updated_by}.
     * @param ahora           instante de generacion (UTC), tomado del reloj comun.
     * @return la Notificacion lista para persistir, en {@code pendiente}.
     * @throws ReglaNegocioException si faltan campos obligatorios o se exceden las
     *         longitudes maximas (422).
     */
    public static Notificacion generar(
            TipoEventoNotificacion eventoOrigen,
            CanalNotificacion canal,
            String destinatario,
            String asunto,
            String contenido,
            boolean esMarketing,
            String referenciaTipo,
            UUID referenciaId,
            String actor,
            Instant ahora) {
        if (eventoOrigen == null) {
            throw new ReglaNegocioException("La Notificacion debe indicar el evento de origen.");
        }
        if (canal == null) {
            throw new ReglaNegocioException("La Notificacion debe indicar el canal de entrega.");
        }
        String destinatarioNormalizado = normalizarObligatorio(destinatario, "destinatario");
        if (destinatarioNormalizado.length() > MAX_DESTINATARIO) {
            throw new ReglaNegocioException(
                    "El destinatario de la Notificacion excede " + MAX_DESTINATARIO + " caracteres.");
        }
        String contenidoNormalizado = normalizarObligatorio(contenido, "contenido");
        if (contenidoNormalizado.length() > MAX_CONTENIDO) {
            throw new ReglaNegocioException(
                    "El contenido de la Notificacion excede " + MAX_CONTENIDO + " caracteres.");
        }
        String asuntoNormalizado = (asunto == null || asunto.isBlank()) ? null : asunto.strip();
        if (asuntoNormalizado != null && asuntoNormalizado.length() > MAX_ASUNTO) {
            throw new ReglaNegocioException(
                    "El asunto de la Notificacion excede " + MAX_ASUNTO + " caracteres.");
        }

        Notificacion notificacion = new Notificacion();
        notificacion.id = UUID.randomUUID();
        notificacion.eventoOrigen = eventoOrigen;
        notificacion.canal = canal;
        notificacion.destinatario = destinatarioNormalizado;
        notificacion.asunto = asuntoNormalizado;
        notificacion.contenido = contenidoNormalizado;
        notificacion.esMarketing = esMarketing;
        notificacion.referenciaTipo =
                (referenciaTipo == null || referenciaTipo.isBlank()) ? null : referenciaTipo.strip();
        notificacion.referenciaId = referenciaId;
        notificacion.estado = EstadoNotificacion.PENDIENTE;
        notificacion.creadaEn = (ahora == null) ? Instant.now() : ahora;
        notificacion.setCreatedBy(actor);
        notificacion.setUpdatedBy(actor);
        return notificacion;
    }

    /**
     * Marca la Notificacion como {@link EstadoNotificacion#ENVIADA} y fija el
     * instante de envio (Req 46.3).
     *
     * @param cuando instante del envio exitoso (UTC); obligatorio.
     * @param actor  identificador de quien actualiza, para {@code updated_by}.
     */
    public void marcarEnviada(Instant cuando, String actor) {
        this.estado = EstadoNotificacion.ENVIADA;
        this.enviadaEn = (cuando == null) ? Instant.now() : cuando;
        this.setUpdatedBy(actor);
    }

    /**
     * Marca la Notificacion como {@link EstadoNotificacion#FALLIDA} tras agotar la
     * politica de reintentos (Req 46.3).
     *
     * @param actor identificador de quien actualiza, para {@code updated_by}.
     */
    public void marcarFallida(String actor) {
        this.estado = EstadoNotificacion.FALLIDA;
        this.setUpdatedBy(actor);
    }

    /**
     * Marca la Notificacion como {@link EstadoNotificacion#OMITIDA} registrando el
     * motivo de la omision (Req 46.7). No se realiza ningun envio.
     *
     * @param motivo motivo de la omision, sin datos sensibles; &le; 300.
     * @param actor  identificador de quien actualiza, para {@code updated_by}.
     */
    public void marcarOmitida(String motivo, String actor) {
        this.estado = EstadoNotificacion.OMITIDA;
        this.motivoOmision = (motivo == null) ? null
                : motivo.strip().substring(0, Math.min(motivo.strip().length(), MAX_MOTIVO_OMISION));
        this.setUpdatedBy(actor);
    }

    private static String normalizarObligatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El campo '" + campo + "' de la Notificacion es obligatorio.");
        }
        return valor.strip();
    }

    public UUID getId() {
        return id;
    }

    public TipoEventoNotificacion getEventoOrigen() {
        return eventoOrigen;
    }

    public CanalNotificacion getCanal() {
        return canal;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getContenido() {
        return contenido;
    }

    public boolean isEsMarketing() {
        return esMarketing;
    }

    public String getReferenciaTipo() {
        return referenciaTipo;
    }

    public UUID getReferenciaId() {
        return referenciaId;
    }

    public EstadoNotificacion getEstado() {
        return estado;
    }

    public String getMotivoOmision() {
        return motivoOmision;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getEnviadaEn() {
        return enviadaEn;
    }
}
