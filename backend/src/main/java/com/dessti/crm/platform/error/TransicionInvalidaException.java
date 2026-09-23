package com.dessti.crm.platform.error;

/**
 * Se lanza cuando se intenta una transicion de estado no permitida por la
 * maquina de estados de una entidad (por ejemplo, partir de un estado final).
 * Se mapea a HTTP 409 (Conflict): el estado actual del recurso impide la
 * operacion solicitada.
 */
public class TransicionInvalidaException extends ErrorNegocio {

    public TransicionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
