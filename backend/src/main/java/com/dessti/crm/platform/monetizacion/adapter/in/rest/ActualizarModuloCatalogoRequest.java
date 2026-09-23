package com.dessti.crm.platform.monetizacion.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo para actualizar nombre/descripcion de un modulo del catalogo. */
public record ActualizarModuloCatalogoRequest(
        @NotBlank @Size(max = 120) String nombre,
        @Size(max = 500) String descripcion) {
}