package com.dessti.crm.social.application;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.TipoMensaje;

/**
 * Solicitud inmutable de envio de un Mensaje_Social hacia el proveedor a traves del
 * {@link MensajeriaSocialPort} (Req 64.6, 64.7, 64.11). Es un record de frontera
 * hexagonal, sin tipos de dominio de persistencia ni de framework, para mantener el
 * puerto estable y portable (mismo patron que {@code SolicitudTimbrado}).
 *
 * <p>Las guardas de negocio (Ventana_Servicio, Req 64.7; Opt_In, Req 64.8) las
 * aplica la capa de aplicacion <strong>antes</strong> de construir esta solicitud;
 * el adaptador solo despacha.</p>
 *
 * @param canal            Canal_Social por el que se envia; obligatorio.
 * @param credencialesRef  referencia al secreto de la cuenta (NUNCA el valor,
 *                         Req 11); el adaptador resuelve el token desde el vault.
 * @param destinatario     identificador del destinatario en el canal (numero WA /
 *                         psid / igsid); obligatorio.
 * @param tipo             tipo del mensaje (texto/plantilla/interactivo); obligatorio.
 * @param contenido        contenido del mensaje o, para plantilla, el nombre/cuerpo
 *                         de la Plantilla_Mensaje; obligatorio.
 */
public record SolicitudEnvioSocial(
        CanalSocial canal,
        String credencialesRef,
        String destinatario,
        TipoMensaje tipo,
        String contenido) {
}
