package com.dessti.crm.calidad.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para registrar una No_Conformidad (Req 70.2).
 *
 * @param origen          etiqueta del origen; obligatoria.
 * @param descripcion     descripcion; obligatoria.
 * @param procesoAfectado proceso afectado; obligatorio.
 */
public record RegistrarNoConformidadRequest(
        @NotBlank String origen,
        @NotBlank String descripcion,
        @NotBlank String procesoAfectado) {
}
