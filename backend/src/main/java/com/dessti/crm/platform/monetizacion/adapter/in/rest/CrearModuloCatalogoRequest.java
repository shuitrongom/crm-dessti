package com.dessti.crm.platform.monetizacion.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo para dar de alta un modulo del catalogo. */
public record CrearModuloCatalogoRequest(
        @NotBlank @Size(max = 60) String clave,
        @NotBlank @Size(max = 120) String nombre,
        @Size(max = 500) String descripcion) {
}