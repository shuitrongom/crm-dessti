package com.dessti.crm.comercial.actividad.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion REST de edicion del contenido de una Actividad (asunto y
 * descripcion). El tipo, el Cliente y la Oportunidad son inmutables.
 *
 * @param asunto      nuevo asunto; obligatorio (1..200).
 * @param descripcion nueva descripcion; opcional (<= 4000).
 */
public record EditarActividadRequest(
        @NotBlank(message = "El asunto de la Actividad es obligatorio.")
        @Size(max = 200, message = "El asunto no puede exceder 200 caracteres.")
        String asunto,

        @Size(max = 4000, message = "La descripcion no puede exceder 4000 caracteres.")
        String descripcion) {
}
