package com.dessti.crm.notificaciones.adapter.out.canal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dessti.crm.notificaciones.application.MensajeNotificacion;
import com.dessti.crm.notificaciones.application.NotificadorCorreoPort;
import com.dessti.crm.notificaciones.application.ResultadoEnvio;

/**
 * Adaptador por defecto (placeholder) del {@link NotificadorCorreoPort} que se
 * limita a registrar el envio en el log de la aplicacion, devolviendo siempre exito
 * (Req 46.1, 46.2). Permite que el flujo de notificaciones funcione de extremo a
 * extremo sin acoplar el sistema a un proveedor de correo concreto.
 *
 * <h2>Sustituibilidad</h2>
 * <p>Se registra como bean por defecto en {@code NotificacionesConfig} solo si
 * <strong>no hay otra implementacion</strong> del puerto
 * ({@code @ConditionalOnMissingBean}), de modo que el adaptador real (SMTP/API) lo
 * reemplace automaticamente cuando se aporte.</p>
 *
 * <h2>Sin secretos (Req 46.4, 11)</h2>
 * <p>No usa credenciales ni escribe datos sensibles: solo registra el destinatario y
 * el identificador de la Notificacion para trazabilidad.</p>
 */
public class NotificadorCorreoRegistroLog implements NotificadorCorreoPort {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoRegistroLog.class);

    @Override
    public ResultadoEnvio enviarCorreo(MensajeNotificacion mensaje) {
        log.info("[NOTIFICACION_CORREO] notificacion={} destinatario='{}' "
                        + "(adaptador de registro por defecto; integrar proveedor real)",
                mensaje.notificacionId(), mensaje.destinatario());
        return ResultadoEnvio.exitoso();
    }
}
