package com.dessti.crm.social.application;

import com.dessti.crm.social.domain.CanalSocial;

/**
 * Comando de aplicacion que describe un Mensaje_Social <strong>entrante</strong>
 * recibido por webhook (Req 64.3, 64.4). Objeto de entrada inmutable que el
 * controlador de webhooks construye tras validar la firma del evento
 * (X-Hub-Signature-256, HMAC-SHA256).
 *
 * @param canal                 Canal_Social por el que llego; obligatorio.
 * @param identificadorCuenta   identificador externo de la cuenta que recibio el
 *                              evento (numero WA / page id / ig id); obligatorio.
 * @param remitenteExterno      identificador del remitente en el canal; obligatorio.
 * @param contenido             contenido del mensaje entrante; obligatorio.
 * @param externoId             id del mensaje en el proveedor; opcional.
 * @param nombreMostrado        nombre visible del remitente si el evento lo trae;
 *                              opcional (para la captura de leads).
 */
public record MensajeEntranteCommand(
        CanalSocial canal,
        String identificadorCuenta,
        String remitenteExterno,
        String contenido,
        String externoId,
        String nombreMostrado) {
}
