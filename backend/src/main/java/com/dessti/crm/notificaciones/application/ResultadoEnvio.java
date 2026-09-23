package com.dessti.crm.notificaciones.application;

/**
 * Resultado inmutable de un intento de entrega de una Notificacion por un puerto de
 * salida de canal (correo/WhatsApp/social) (Req 46.3). Es un contrato estable, sin
 * tipos de dominio ni de persistencia, para mantener los puertos portables.
 *
 * <p>El campo {@link #mensajeError()} solo se informa en fallo y NO debe contener
 * datos sensibles (Req 46.4). El campo {@link #intentos()} indica cuantos intentos
 * realizo internamente el adaptador (normalmente 1 cuando la politica de reintentos
 * la gobierna la {@code ServicioNotificaciones}).</p>
 *
 * @param exito        {@code true} si la entrega al proveedor tuvo exito.
 * @param mensajeError motivo del fallo, sin datos sensibles; {@code null} en exito.
 * @param intentos     numero de intentos internos del adaptador (&ge; 1).
 */
public record ResultadoEnvio(boolean exito, String mensajeError, int intentos) {

    /**
     * Crea un resultado exitoso de un unico intento.
     *
     * @return resultado con {@code exito=true} e {@code intentos=1}.
     */
    public static ResultadoEnvio exitoso() {
        return new ResultadoEnvio(true, null, 1);
    }

    /**
     * Crea un resultado fallido de un unico intento con el motivo indicado.
     *
     * @param mensajeError motivo del fallo, sin datos sensibles.
     * @return resultado con {@code exito=false} e {@code intentos=1}.
     */
    public static ResultadoEnvio fallido(String mensajeError) {
        return new ResultadoEnvio(false, mensajeError, 1);
    }
}
