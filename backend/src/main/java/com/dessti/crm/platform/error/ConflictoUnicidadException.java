package com.dessti.crm.platform.error;

/**
 * Se lanza cuando una operacion viola una restriccion de unicidad de negocio
 * (por ejemplo, un identificador fiscal duplicado dentro del mismo tenant,
 * Req 5.3, 23.6, 29.3). Se mapea a HTTP 409.
 */
public class ConflictoUnicidadException extends ErrorNegocio {

    public ConflictoUnicidadException(String mensaje) {
        super(mensaje);
    }
}
