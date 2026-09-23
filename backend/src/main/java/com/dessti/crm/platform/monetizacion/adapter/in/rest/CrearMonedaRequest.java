package com.dessti.crm.platform.monetizacion.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo para dar de alta una moneda (ISO 4217). */
public record CrearMonedaRequest(
        @NotBlank @Size(min = 3, max = 3) String codigo,
        @NotBlank @Size(max = 60) String nombre) {
}