package com.dessti.crm.calidad.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para identificar un Riesgo (Req 70.3). El nivel derivado lo
 * calcula el dominio; no se recibe del cliente.
 *
 * @param descripcion  descripcion del riesgo; obligatoria.
 * @param probabilidad etiqueta de la probabilidad (baja/media/alta); obligatoria.
 * @param impacto      etiqueta del impacto (bajo/medio/alto); obligatoria.
 * @param acciones     acciones para abordarlo; opcional.
 */
public record IdentificarRiesgoRequest(
        @NotBlank String descripcion,
        @NotBlank String probabilidad,
        @NotBlank String impacto,
        String acciones) {
}
