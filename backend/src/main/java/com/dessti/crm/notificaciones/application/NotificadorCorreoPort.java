package com.dessti.crm.notificaciones.application;

/**
 * Puerto de <strong>salida</strong> hacia el proveedor de <em>correo electronico</em>
 * (Req 46.1, 46.2). Frontera hexagonal que desacopla el nucleo de notificaciones de
 * la integracion concreta con el proveedor de correo (SMTP/API), de modo que el
 * adaptador real pueda intercambiarse sin tocar la aplicacion.
 *
 * <h2>Contrato</h2>
 * <p>{@link #enviarCorreo(MensajeNotificacion)} intenta entregar el mensaje al
 * proveedor y devuelve un {@link ResultadoEnvio} (exito o fallo con motivo, sin
 * datos sensibles, Req 46.4). La <em>politica de reintentos</em> (Req 46.3) la
 * gobierna la {@code ServicioNotificaciones}, que reintentara este metodo hasta el
 * maximo configurado; el adaptador debe realizar un unico intento por llamada.</p>
 *
 * <h2>Secretos (Req 11)</h2>
 * <p>Las credenciales del proveedor de correo se resuelven exclusivamente desde la
 * gestion de secretos ({@code crm.notificaciones.correo.*} sobre variables de
 * entorno) y nunca se embeben en el codigo ni se escriben en logs. Mientras no haya
 * adaptador real, {@link NotificadorCorreoRegistroLog} cubre el arranque y las
 * pruebas registrando el envio en el log.</p>
 */
public interface NotificadorCorreoPort {

    /**
     * Intenta entregar una Notificacion por correo electronico (Req 46.1, 46.2).
     *
     * @param mensaje mensaje minimo a entregar; obligatorio (Req 46.4).
     * @return el resultado del intento (exito, o fallo con motivo sin secretos).
     */
    ResultadoEnvio enviarCorreo(MensajeNotificacion mensaje);
}
