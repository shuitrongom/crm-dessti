package com.dessti.crm.platform.respaldo.application;

/**
 * Excepcion de dominio de la capacidad de respaldo/recuperacion (Req 50). Se
 * lanza cuando una operacion de respaldo o restauracion falla de forma
 * controlada. Su mensaje NUNCA contiene material de llaves ni datos sensibles
 * (Req 67.2).
 */
public class RespaldoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RespaldoException(String mensaje) {
        super(mensaje);
    }

    public RespaldoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
