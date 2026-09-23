package com.dessti.crm.platform.error;

/**
 * Se lanza cuando una direccion de origen supera el limite de tasa permitido
 * (Req 2.4). Se mapea a HTTP 429 (Too Many Requests): la peticion excedente se
 * rechaza sin procesarse.
 *
 * <p>El mensaje al cliente es <b>generico</b> (indica solo que se supero el
 * limite de tasa) y no revela contadores internos, IPs ni ventanas de conteo,
 * para no facilitar el ajuste fino de un abuso.</p>
 */
public class LimiteSolicitudesExcedidoException extends ErrorNegocio {

    public LimiteSolicitudesExcedidoException(String mensaje) {
        super(mensaje);
    }
}
