package com.dessti.crm.platform.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Descripcion inmutable de una alerta de auditoria que se ha disparado
 * (Req 10.11), lista para notificarse a traves del
 * {@link NotificadorAlertasPort}.
 *
 * <p>No contiene secretos: solo metadatos del patron detectado (patron, tenant,
 * conteo observado, umbral, ventana y destinatarios). El contenido del mensaje
 * final lo compone el adaptador de notificaciones (Tarea 43).</p>
 *
 * @param tenantId       empresa afectada; {@code null} para el ambito de
 *                       plataforma.
 * @param patron         patron sensible detectado.
 * @param conteoObservado numero de eventos observados en la ventana.
 * @param umbral         umbral configurado que se supero.
 * @param destinatarios  destinatarios de la Notificacion.
 * @param detectadaUtc   momento (UTC) de la deteccion.
 */
public record AlertaDisparada(
        UUID tenantId,
        PatronAlerta patron,
        long conteoObservado,
        int umbral,
        String destinatarios,
        Instant detectadaUtc) {
}
