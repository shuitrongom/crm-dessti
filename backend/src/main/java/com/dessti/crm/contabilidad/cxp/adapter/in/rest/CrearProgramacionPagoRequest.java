package com.dessti.crm.contabilidad.cxp.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para crear una Programacion_Pago (Req 42.2). DTO de entrada
 * del contrato REST, distinto del comando de aplicacion
 * {@link com.dessti.crm.contabilidad.cxp.application.CrearProgramacionPagoCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * @param cuentaPorPagarId Cuenta_Por_Pagar a la que corresponde el pago; obligatorio.
 * @param fechaProgramada  fecha programada del pago; obligatoria.
 * @param monto            monto programado; obligatorio y positivo (Req 42.2).
 */
public record CrearProgramacionPagoRequest(
        @NotNull UUID cuentaPorPagarId,
        @NotNull LocalDate fechaProgramada,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal monto) {
}
