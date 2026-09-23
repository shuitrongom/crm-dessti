package com.dessti.crm.notificaciones.adapter.out.canal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dessti.crm.notificaciones.application.MensajeNotificacion;
import com.dessti.crm.notificaciones.application.NotificadorSocialPort;
import com.dessti.crm.notificaciones.application.ResultadoEnvio;
import com.dessti.crm.notificaciones.domain.CanalNotificacion;

/**
 * Adaptador por defecto (placeholder) del {@link NotificadorSocialPort} que se
 * limita a registrar el envio por Canal_Social en el log, devolviendo siempre exito
 * (Req 46.6). Es el punto de costura que el modulo social podra implementar mas
 * adelante para reutilizar su integracion; mientras tanto permite arrancar y probar.
 *
 * <h2>Independencia de modulos</h2>
 * <p>Este adaptador NO importa ninguna clase del modulo social. Se registra con
 * {@code @ConditionalOnMissingBean} en {@code NotificacionesConfig}, de modo que el
 * bean real que aporte el modulo social lo reemplace automaticamente.</p>
 *
 * <h2>Semantica documentada (Req 46.6)</h2>
 * <p>La implementacion real debe respetar Ventana_Servicio y usar una
 * Plantilla_Mensaje aprobada fuera de la ventana. Este placeholder no evalua la
 * ventana; solo deja constancia del envio. La guarda de Opt_In (Req 46.7) ya la
 * aplico la {@code ServicioNotificaciones} antes de invocar este puerto.</p>
 */
public class NotificadorSocialRegistroLog implements NotificadorSocialPort {

    private static final Logger log = LoggerFactory.getLogger(NotificadorSocialRegistroLog.class);

    @Override
    public ResultadoEnvio enviarPorCanalSocial(CanalNotificacion canal, MensajeNotificacion mensaje) {
        log.info("[NOTIFICACION_SOCIAL] canal={} notificacion={} destinatario='{}' "
                        + "(adaptador de registro por defecto; integrar modulo social real, "
                        + "respetar Ventana_Servicio/Plantilla_Mensaje)",
                canal.valorBd(), mensaje.notificacionId(), mensaje.destinatario());
        return ResultadoEnvio.exitoso();
    }
}
