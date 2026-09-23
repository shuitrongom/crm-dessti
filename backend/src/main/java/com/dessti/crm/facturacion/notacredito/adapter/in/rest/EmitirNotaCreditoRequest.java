package com.dessti.crm.facturacion.notacredito.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para emitir una Nota de Credito (CFDI de egreso) (Req 37.1,
 * 37.2). DTO de entrada del contrato REST, distinto del comando de aplicacion
 * {@link com.dessti.crm.facturacion.notacredito.application.EmitirNotaCreditoCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * @param facturaId Factura timbrada referenciada; obligatoria (Req 37.1).
 * @param monto     monto del CFDI de egreso; obligatorio y positivo (Req 37.2). El
 *                  tope por el saldo disponible de la Factura lo valida la
 *                  aplicacion (422).
 */
public record EmitirNotaCreditoRequest(
        @NotNull UUID facturaId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal monto) {
}
