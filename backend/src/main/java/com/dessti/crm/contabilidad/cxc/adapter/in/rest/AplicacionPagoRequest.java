package com.dessti.crm.contabilidad.cxc.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Linea de aplicacion de un pago a una Factura concreta dentro de un
 * {@link RegistrarPagoClienteRequest} (Req 36.2). DTO de entrada del contrato REST.
 *
 * @param facturaId Factura a la que se aplica la porcion de pago; obligatoria.
 * @param monto     monto aplicado a esa Factura; obligatorio y positivo. El tope por
 *                  el saldo pendiente lo valida la aplicacion (422, Req 36.3).
 */
public record AplicacionPagoRequest(
        @NotNull UUID facturaId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal monto) {
}
