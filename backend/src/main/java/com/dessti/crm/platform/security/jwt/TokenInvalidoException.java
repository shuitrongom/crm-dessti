package com.dessti.crm.platform.security.jwt;

/**
 * Se lanza cuando un JWT no puede validarse: firma invalida, token expirado,
 * malformado o de un tipo inesperado (Req 1.6, 1.9).
 *
 * <p>El mensaje es <b>generico</b> a proposito y no revela la causa exacta al
 * cliente (se mapea a HTTP 401). El detalle tecnico se registra unicamente en
 * el servidor.</p>
 */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String mensaje) {
        super(mensaje);
    }

    public TokenInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
