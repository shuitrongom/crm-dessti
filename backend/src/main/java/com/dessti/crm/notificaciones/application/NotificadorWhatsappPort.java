package com.dessti.crm.notificaciones.application;

/**
 * Puerto de <strong>salida</strong> hacia el proveedor de <em>WhatsApp</em>
 * (Req 46.1, 46.2). Frontera hexagonal que desacopla el nucleo de notificaciones de
 * la integracion concreta con la API de WhatsApp, de modo que el adaptador real
 * pueda intercambiarse sin tocar la aplicacion.
 *
 * <h2>Contrato</h2>
 * <p>{@link #enviarWhatsapp(MensajeNotificacion)} intenta entregar el mensaje al
 * proveedor y devuelve un {@link ResultadoEnvio} (exito o fallo con motivo, sin
 * datos sensibles, Req 46.4). La politica de reintentos (Req 46.3) la gobierna la
 * {@code ServicioNotificaciones}; el adaptador realiza un unico intento por
 * llamada.</p>
 *
 * <p><strong>Nota (Req 46.6):</strong> cuando el envio de WhatsApp deba tratarse
 * como Canal_Social respetando Ventana_Servicio/Plantilla/Opt_In, la
 * {@code ServicioNotificaciones} enruta a {@link NotificadorSocialPort} en lugar de
 * este puerto. Este puerto cubre el envio transaccional directo por WhatsApp.</p>
 *
 * <h2>Secretos (Req 11)</h2>
 * <p>Las credenciales de WhatsApp se resuelven exclusivamente desde la gestion de
 * secretos ({@code crm.notificaciones.whatsapp.*} sobre variables de entorno) y
 * nunca se escriben en logs. Mientras no haya adaptador real,
 * {@link NotificadorWhatsappRegistroLog} cubre el arranque y las pruebas.</p>
 */
public interface NotificadorWhatsappPort {

    /**
     * Intenta entregar una Notificacion por WhatsApp (Req 46.1, 46.2).
     *
     * @param mensaje mensaje minimo a entregar; obligatorio (Req 46.4).
     * @return el resultado del intento (exito, o fallo con motivo sin secretos).
     */
    ResultadoEnvio enviarWhatsapp(MensajeNotificacion mensaje);
}
