package com.dessti.crm.platform.error;

/**
 * Se lanza cuando una regla de negocio se incumple (por ejemplo, saldo
 * excedido, existencias insuficientes, discrepancia de tres vias o poliza no
 * balanceada). Se mapea a HTTP 422 (Unprocessable Entity): la peticion es
 * sintacticamente valida pero contraviene una invariante de negocio.
 */
public class ReglaNegocioException extends ErrorNegocio {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
