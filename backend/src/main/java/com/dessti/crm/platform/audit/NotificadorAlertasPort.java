package com.dessti.crm.platform.audit;

/**
 * Puerto de dominio para emitir la Notificacion de una alerta de auditoria
 * disparada (Req 10.11).
 *
 * <p>Desacopla la deteccion de patrones (en {@link ServicioAlertasAuditoria})
 * de la entrega efectiva de la Notificacion, preservando la portabilidad del
 * nucleo. La implementacion real (correo/WhatsApp con politica de reintentos)
 * pertenece al modulo de notificaciones.</p>
 *
 * <p><strong>TODO (Tarea 43 - notificaciones):</strong> integrar este puerto con
 * el {@code NotificacionPort} del modulo de notificaciones para enviar la alerta
 * al destinatario configurado por el canal correspondiente. Mientras tanto se
 * provee {@link NotificadorAlertasRegistroLog} como implementacion minima
 * (placeholder) que unicamente registra la alerta en el log.</p>
 */
public interface NotificadorAlertasPort {

    /**
     * Emite la Notificacion de una alerta disparada.
     *
     * @param alerta metadatos de la alerta detectada (sin secretos).
     */
    void notificar(AlertaDisparada alerta);
}
