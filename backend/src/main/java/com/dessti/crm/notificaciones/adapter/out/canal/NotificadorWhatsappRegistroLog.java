package com.dessti.crm.notificaciones.adapter.out.canal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dessti.crm.notificaciones.application.MensajeNotificacion;
import com.dessti.crm.notificaciones.application.NotificadorWhatsappPort;
import com.dessti.crm.notificaciones.application.ResultadoEnvio;

/**
 * Adaptador por defecto (placeholder) del {@link NotificadorWhatsappPort} que se
 * limita a registrar el envio en el log de la aplicacion, devolviendo siempre exito
 * (Req 46.1, 46.2). Permite ejercitar el flujo sin acoplar el sistema a la API de
 * WhatsApp.
 *
 * <h2>Sustituibilidad</h2>
 * <p>Se registra con {@code @ConditionalOnMissingBean} en
 * {@code NotificacionesConfig}: el adaptador real de WhatsApp lo reemplaza
 * automaticamente cuando se aporte.</p>
 *
 * <h2>Sin secretos (Req 46.4, 11)</h2>
 * <p>No usa credenciales ni escribe datos sensibles.</p>
 */
public class NotificadorWhatsappRegistroLog implements NotificadorWhatsappPort {

    private static final Logger log = LoggerFactory.getLogger(NotificadorWhatsappRegistroLog.class);

    @Override
    public ResultadoEnvio enviarWhatsapp(MensajeNotificacion mensaje) {
        log.info("[NOTIFICACION_WHATSAPP] notificacion={} destinatario='{}' "
                        + "(adaptador de registro por defecto; integrar proveedor real)",
                mensaje.notificacionId(), mensaje.destinatario());
        return ResultadoEnvio.exitoso();
    }
}
