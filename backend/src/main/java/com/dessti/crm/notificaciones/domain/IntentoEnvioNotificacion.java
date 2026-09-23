package com.dessti.crm.notificaciones.domain;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del historial de intentos de envio de una {@link Notificacion}
 * (Req 46.3): registra el resultado de <em>cada</em> intento realizado conforme a la
 * politica de reintentos configurable. Mapeada sobre la tabla
 * {@code intento_envio_notificacion} de la migracion V42 (Req 46, 23).
 *
 * <p>Es un registro <strong>append-only</strong>: por cada intento (exitoso o
 * fallido) se persiste una fila con el numero de intento, el resultado y, en caso de
 * fallo, el mensaje de error (sin datos sensibles, Req 46.4). Extiende
 * {@link TenantScopedEntity} para heredar {@code tenant_id}, {@code version} y las
 * marcas de auditoria; el historial no se modifica una vez escrito.</p>
 */
@Entity
@Table(name = "intento_envio_notificacion")
public class IntentoEnvioNotificacion extends TenantScopedEntity {

    /** Longitud maxima del mensaje de error, coherente con V42 (VARCHAR(500)). */
    public static final int MAX_MENSAJE_ERROR = 500;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Notificacion a la que pertenece este intento (Req 46.3). */
    @Column(name = "notificacion_id", nullable = false, updatable = false)
    private UUID notificacionId;

    /** Numero de intento, 1-indexado y creciente (Req 46.3). */
    @Column(name = "numero_intento", nullable = false, updatable = false)
    private int numeroIntento;

    /** Resultado del intento: {@code true} si el envio tuvo exito (Req 46.3). */
    @Column(name = "exito", nullable = false, updatable = false)
    private boolean exito;

    /** Mensaje de error del proveedor cuando el intento fallo; sin secretos (Req 46.4). */
    @Column(name = "mensaje_error", updatable = false)
    private String mensajeError;

    /** Instante del intento (UTC). */
    @Column(name = "intentado_en", nullable = false, updatable = false)
    private Instant intentadoEn;

    protected IntentoEnvioNotificacion() {
        // Requerido por JPA.
    }

    /**
     * Registra un intento de envio de la Notificacion (Req 46.3).
     *
     * @param notificacionId Notificacion a la que pertenece; obligatorio.
     * @param numeroIntento  numero de intento &ge; 1.
     * @param exito          si el intento tuvo exito.
     * @param mensajeError   mensaje de error si fallo; {@code null} en exito; &le; 500.
     * @param cuando         instante del intento (UTC).
     * @param actor          identificador de quien lo genera, para las marcas de auditoria.
     * @return el intento listo para persistir.
     * @throws ReglaNegocioException si {@code numeroIntento < 1} (422).
     */
    public static IntentoEnvioNotificacion registrar(
            UUID notificacionId,
            int numeroIntento,
            boolean exito,
            String mensajeError,
            Instant cuando,
            String actor) {
        if (notificacionId == null) {
            throw new ReglaNegocioException("El intento debe referenciar una Notificacion.");
        }
        if (numeroIntento < 1) {
            throw new ReglaNegocioException("El numero de intento debe ser mayor o igual a 1.");
        }
        IntentoEnvioNotificacion intento = new IntentoEnvioNotificacion();
        intento.id = UUID.randomUUID();
        intento.notificacionId = notificacionId;
        intento.numeroIntento = numeroIntento;
        intento.exito = exito;
        intento.mensajeError = recortar(mensajeError);
        intento.intentadoEn = (cuando == null) ? Instant.now() : cuando;
        intento.setCreatedBy(actor);
        intento.setUpdatedBy(actor);
        return intento;
    }

    private static String recortar(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return null;
        }
        String limpio = mensaje.strip();
        return limpio.length() > MAX_MENSAJE_ERROR
                ? limpio.substring(0, MAX_MENSAJE_ERROR)
                : limpio;
    }

    public UUID getId() {
        return id;
    }

    public UUID getNotificacionId() {
        return notificacionId;
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
