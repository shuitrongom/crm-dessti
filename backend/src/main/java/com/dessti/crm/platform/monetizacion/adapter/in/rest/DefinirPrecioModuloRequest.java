package com.dessti.crm.platform.monetizacion.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo para definir el precio de un modulo en una moneda (lista o especial). */
public record DefinirPrecioModuloRequest(
        @NotBlank @Size(min = 3, max = 3) String monedaCodigo,
        @NotNull @DecimalMin("0.00") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2)
        BigDecimal precio) {
}