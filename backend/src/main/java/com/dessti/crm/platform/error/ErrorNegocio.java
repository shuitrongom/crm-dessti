package com.dessti.crm.platform.error;

/**
 * Excepcion base de dominio del CRM.
 *
 * <p>Toda excepcion de negocio deriva de esta clase para que el manejador
 * global de errores pueda traducirla a una respuesta Problem Details (RFC 7807)
 * de forma uniforme, sin filtrar detalles internos. El mensaje debe ser un
 * mensaje de negocio en espanol apto para mostrar al Usuario (Req 56.2).</p>
 */
public abstract class ErrorNegocio extends RuntimeException {

    protected ErrorNegocio(String mensaje) {
        super(mensaje);
    }

    protected ErrorNegocio(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
