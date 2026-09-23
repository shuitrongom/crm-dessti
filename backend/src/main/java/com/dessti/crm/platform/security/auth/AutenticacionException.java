package com.dessti.crm.platform.security.auth;

/**
 * Error de autenticacion: credenciales invalidas o Token_Refresco no valido /
 * expirado (Req 1.3, 1.6, 1.9). Se mapea a HTTP 401.
 *
 * <p>El mensaje es <b>generico</b> a proposito para no revelar cual credencial
 * fallo ni la causa exacta (Req 1.3).</p>
 */
public class AutenticacionException extends RuntimeException {

    public AutenticacionException(String mensaje) {
        super(mensaje);
    }
}
