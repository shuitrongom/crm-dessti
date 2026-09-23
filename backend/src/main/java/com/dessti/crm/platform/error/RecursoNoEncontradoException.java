package com.dessti.crm.platform.error;

/**
 * Se lanza cuando un recurso solicitado no existe o pertenece a otra Empresa
 * (tenant). En ambos casos se responde <b>404</b> para no revelar la existencia
 * del recurso a otro tenant (Req 23.3). Se mapea a HTTP 404.
 */
public class RecursoNoEncontradoException extends ErrorNegocio {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
