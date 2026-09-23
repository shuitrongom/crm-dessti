package com.dessti.crm.social.domain;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA {@code intento_publicacion}: registro <strong>inmutable</strong> del
 * resultado de un intento de publicar una {@link PublicacionSocial} a traves del
 * adaptador (Req 65.6). Cada intento —exitoso o fallido— se persiste para dejar
 * trazabilidad de la politica de reintentos, mapeada sobre la tabla
 * {@code intento_publicacion} de la migracion V43.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}.
 * El mapeo de columnas coincide <em>exactamente</em> con V43.</p>
 */
@Entity
@Table(name = "intento_publicacion")
public class IntentoPublicacion extends TenantScopedEntity {

    /** Longitud maxima del mensaje de error (coincide con VARCHAR(500) de V43). */
    static final int MAX_MENSAJE_ERROR = 500;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Publicacion_Social a la que pertenece el intento; inmutable (Req 65.6). */
    @Column(name = "publicacion_social_id", nullable = false, updatable = false)
    private UUID publicacionSocialId;

    /** Numero de intento (1-based); inmutable (Req 65.6). */
    @Column(name = "numero_intento", nullable = false, updatable = false)
    private int numeroIntento;

    /** Resultado del intento: {@code true} si el proveedor confirmo la publicacion. */
    @Column(name = "exito", nullable = false, updatable = false)
    private boolean exito;

    /** Motivo del fallo del intento; {@code null} en un intento exitoso. */
    @Column(name = "mensaje_error", length = MAX_MENSAJE_ERROR, updatable = false)
    private String mensajeError;

    /** Instante del intento (UTC); inmutable (Req 65.6). */
    @Column(name = "intentado_en", nullable = false, updatable = false)
    private Instant intentadoEn;

    protected IntentoPublicacion() {
        // Requerido por JPA.
    }

    /**
     * Registra un intento de publicacion (Req 65.6). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param publicacionSocialId Publicacion_Social intentada; obligatorio.
     * @param numeroIntento       numero de intento (>= 1); obligatorio.
     * @param exito               {@code true} si el intento fue exitoso.
     * @param mensajeError        motivo del fallo; {@code null} si exitoso.
     * @param intentadoEn         instante del intento (UTC); obligatorio.
     * @param actor               identificador de quien ejecuta (auditoria).
     * @return el IntentoPublicacion listo para persistir.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static IntentoPublicacion registrar(UUID publicacionSocialId, int numeroIntento,
                                               boolean exito, String mensajeError,
                                               Instant intentadoEn, String actor) {
        if (publicacionSocialId == null) {
            throw new ReglaNegocioException("El intento debe asociarse a una Publicacion_Social.");
        }
        if (numeroIntento < 1) {
            throw new ReglaNegocioException("El numero de intento debe ser mayor o igual que 1.");
        }
        if (intentadoEn == null) {
            throw new ReglaNegocioException("El intento debe indicar la marca temporal UTC.");
        }

        IntentoPublicacion intento = new IntentoPublicacion();
        intento.id = UUID.randomUUID();
        intento.publicacionSocialId = publicacionSocialId;
        intento.numeroIntento = numeroIntento;
        intento.exito = exito;
        intento.mensajeError = exito ? null : normalizarMensaje(mensajeError);
        intento.intentadoEn = intentadoEn;
        intento.setCreatedBy(actor);
        intento.setUpdatedBy(actor);
        return intento;
    }

    private static String normalizarMensaje(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return null;
        }
        String limpio = mensaje.strip();
        return (limpio.length() > MAX_MENSAJE_ERROR) ? limpio.substring(0, MAX_MENSAJE_ERROR) : limpio;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPublicacionSocialId() {
        return publicacionSocialId;
    }

    public int getNumeroIntento() {
        return numeroIntento;
    }

    public boolean isExito() {
        return exito;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public Instant getIntentadoEn() {
        return intentadoEn;
    }
}
