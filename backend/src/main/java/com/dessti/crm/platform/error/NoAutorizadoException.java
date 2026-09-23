package com.dessti.crm.platform.error;

/**
 * Se lanza cuando un Usuario autenticado carece del Permiso requerido para una
 * operacion (RBAC con denegacion por defecto, Req 3.2, 3.6). Se mapea a
 * HTTP 403.
 */
public class NoAutorizadoException extends ErrorNegocio {

    public NoAutorizadoException(String mensaje) {
        super(mensaje);
    }
}
