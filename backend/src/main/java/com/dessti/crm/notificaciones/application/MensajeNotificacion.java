package com.dessti.crm.notificaciones.application;

import java.util.UUID;

/**
 * Descripcion inmutable del mensaje minimo a entregar por un puerto de salida de
 * canal (Req 46.4). Transporta solo los datos necesarios para el envio, sin
 * exponer datos sensibles innecesarios.
 *
 * <p>Es el argumento comun de {@link NotificadorCorreoPort},
 * {@link NotificadorWhatsappPort} y {@link NotificadorSocialPort}, de modo que los
 * adaptadores de cada canal consuman un contrato estable e independiente de las
 * entidades de persistencia.</p>
 *
 * @param notificacionId identificador de la Notificacion de origen (para trazas).
 * @param destinatario   correo, telefono o identificador social del destinatario.
 * @param asunto         asunto (correo); puede ser {@code null} en canales sociales.
 * @param contenido      contenido minimo del aviso, ya minimizado (Req 46.4).
 */
public record MensajeNotificacion(
        UUID notificacionId,
        String destinatario,
        String asunto,
        String contenido) {
}
