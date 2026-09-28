package com.dessti.crm.comercial.cotizacion.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/**
 * Cuerpo de la peticion REST para ajustar el descuento global y las retenciones
 * (ISR/IVA) de una Cotizacion en {@code borrador} (V80). Los montos son opcionales
 * (un valor nulo se interpreta como 0) y no negativos; el dominio recalcula el
 * total con el desglose CFDI.
 *
 * @param descuentoGlobal descuento global (monto); opcional, &ge; 0.
 * @param retencionIsr    retencion de ISR (monto); opcional, &ge; 0.
 * @param retencionIva    retencion de IVA (monto); opcional, &ge; 0.
 */
public record AjustesFiscalesRequest(
        @DecimalMin("0.00") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2)
        BigDecimal descuentoGlobal,
        @DecimalMin("0.00") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2)
        BigDecimal retencionIsr,
        @DecimalMin("0.00") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2)
        BigDecimal retencionIva) {
}
