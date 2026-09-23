package com.dessti.crm.social.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para cambiar el estado de una Publicacion_Social (Req 65.3,
 * 65.4). DTO de entrada del contrato REST.
 *
 * @param estado etiqueta del estado destino (borrador/programada/publicada/fallida);
 *               obligatorio. Solo {@code programada} es una transicion manual valida;
 *               {@code publicada}/{@code fallida} se producen al publicar.
 */
public record CambiarEstadoPublicacionRequest(
        @NotBlank @Size(max = 12) String estado) {
}
