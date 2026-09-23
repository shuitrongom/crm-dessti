package com.dessti.crm.notificaciones.application;

/**
 * Puerto de <strong>entrada</strong> del modulo notificaciones (Req 46). Es la
 * fachada que los demas modulos invocan para solicitar la generacion y entrega de
 * una Notificacion ante un evento relevante (Req 46.1), sin conocer los detalles de
 * canal, reintentos, consentimiento ni auditoria.
 *
 * <h2>Responsabilidades de la implementacion (Req 46)</h2>
 * <ul>
 *   <li>Generar la Notificacion (estado inicial {@code pendiente}) a partir de la
 *       {@link SolicitudNotificacion} (Req 46.1), con contenido ya minimizado por el
 *       llamador (Req 46.4).</li>
 *   <li>Aplicar la guarda de Opt_In para marketing en Canal_Social: si falta el
 *       Opt_In vigente, abstenerse de enviar y registrar el motivo de la omision
 *       (Req 46.7).</li>
 *   <li>Despachar al puerto de salida del canal correspondiente y reintentar
 *       conforme a la politica configurable, registrando el resultado de cada
 *       intento (Req 46.2, 46.3).</li>
 *   <li>Enrutar los Canales Sociales a {@link NotificadorSocialPort} reutilizando la
 *       integracion (Req 46.6).</li>
 *   <li>Registrar en el Servicio_Auditoria el envio con actor/evento origen,
 *       destinatario, canal, resultado y marca temporal UTC (Req 46.5).</li>
 * </ul>
 *
 * <p><strong>Generacion ante eventos (Req 46.1):</strong> este puerto aporta el
 * <em>mecanismo</em>. Los modulos productores (Prueba_Diseno, Permiso_Instalacion,
 * Ticket_Servicio, Factura, Nomina) pueden invocar {@link #notificar} al ocurrir su
 * evento relevante; el cableado de esas llamadas en cada modulo es trabajo
 * posterior y no forma parte de este bloque.</p>
 */
public interface NotificacionPort {

    /**
     * Genera una Notificacion ante un evento relevante y la entrega por el canal
     * indicado, aplicando la guarda de Opt_In, la politica de reintentos y la
     * auditoria del envio (Req 46).
     *
     * @param solicitud solicitud con evento, canal, destinatario y contenido
     *                  minimizado; obligatoria.
     * @return el DTO de la Notificacion resultante, con su estado final
     *         ({@code enviada}, {@code fallida} u {@code omitida}).
     */
    NotificacionDto notificar(SolicitudNotificacion solicitud);
}
