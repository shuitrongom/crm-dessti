package com.dessti.crm.contabilidad.polizas.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Linea de un renglon de una Poliza_Contable dentro de un
 * {@link RegistrarPolizaRequest} (Req 38.2, 38.3). DTO de entrada del contrato REST.
 *
 * <p>Exactamente uno de {@code cargo} y {@code abono} debe ser positivo (regla
 * cargo XOR abono, validada por el dominio, 422 si se incumple).</p>
 *
 * @param cuentaContableId Cuenta_Contable afectada; obligatoria.
 * @param cargo            importe del cargo; {@code >= 0} (positivo si es un cargo).
 * @param abono            importe del abono; {@code >= 0} (positivo si es un abono).
 */
public record RenglonPolizaRequest(
        @NotNull UUID cuentaContableId,
        @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal cargo,
        @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal abono) {
}
