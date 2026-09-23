package com.dessti.crm.social.application;

import com.dessti.crm.social.domain.TipoMensaje;

/**
 * Comando de aplicacion para enviar un Mensaje_Social saliente en una Conversacion
 * (Req 64.6, 64.7, 64.8, 64.11). Objeto de entrada inmutable, sin dependencias de
 * framework.
 *
 * <p>La capa de aplicacion aplica, en orden, la guarda de Ventana_Servicio (Req
 * 64.7) y la guarda de Opt_In para marketing (Req 64.8) antes de despachar por el
 * {@code MensajeriaSocialPort} con la politica de reintentos (Req 64.13).</p>
 *
 * @param tipo         tipo del Mensaje_Social (texto/plantilla/interactivo);
 *                     obligatorio.
 * @param contenido    contenido del mensaje o, para plantilla, el nombre/cuerpo de
 *                     la Plantilla_Mensaje; obligatorio.
 * @param esMarketing  {@code true} si es un mensaje de marketing (sujeto a Opt_In,
 *                     Req 64.8).
 */
public record EnviarMensajeCommand(TipoMensaje tipo, String contenido, boolean esMarketing) {
}
