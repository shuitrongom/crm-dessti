package com.dessti.crm.contabilidad.cxp.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para aplicar un pago a una Cuenta_Por_Pagar (Req 42.3,
 * 42.4). DTO de entrada del contrato REST. El {@code tenant_id} y el actor se derivan
 * del contexto (Req 23.4).
 *
 * @param monto monto a aplicar; obligatorio y positivo. El tope por el saldo
 *              pendiente lo valida la aplicacion (422 con el excedente, Req 42.4).
 */
public record AplicarPagoCxpRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal monto) {
}
