package com.dessti.crm.compras.ordencompra.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de una Partida_Orden_Compra en las peticiones REST (Req 31.2). DTO de
 * entrada del contrato, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.compras.ordencompra.application.CrearPartidaOrdenCompraCommand}
 * (Req 12.2).
 *
 * <p>La validacion de campo (Bean Validation -&gt; 400) cubre presencia y rango;
 * las reglas de dominio finas (422) las aplica el dominio, y la existencia del
 * Material (404) la verifica la capa de aplicacion.</p>
 *
 * @param materialId     Material solicitado; obligatorio (Req 31.1).
 * @param cantidad       cantidad; entero en [1, 999,999] (Req 31.2).
 * @param precioUnitario precio unitario; en [0.01, 999,999,999.99] (Req 31.2).
 */
public record PartidaOrdenCompraRequest(
        @NotNull UUID materialId,
        @Min(1) @Max(999999) int cantidad,
        @NotNull
        @DecimalMin(value = "0.01")
        @DecimalMax(value = "999999999.99")
        @Digits(integer = 9, fraction = 2)
        BigDecimal precioUnitario) {
}
