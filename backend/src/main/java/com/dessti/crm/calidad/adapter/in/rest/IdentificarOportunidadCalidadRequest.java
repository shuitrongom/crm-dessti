package com.dessti.crm.calidad.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para identificar una Oportunidad_Calidad (Req 70.3).
 *
 * @param descripcion       descripcion; obligatoria.
 * @param beneficioEsperado beneficio esperado; obligatorio.
 * @param acciones          acciones para aprovecharla; opcional.
 */
public record IdentificarOportunidadCalidadRequest(
        @NotBlank String descripcion,
        @NotBlank String beneficioEsperado,
        String acciones) {
}
