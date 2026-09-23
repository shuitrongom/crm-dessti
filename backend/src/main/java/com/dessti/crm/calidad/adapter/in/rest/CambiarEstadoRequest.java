package com.dessti.crm.calidad.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo generico de la peticion para cambiar el estado de un recurso de calidad
 * (No_Conformidad, Accion_Correctiva, Riesgo, Oportunidad_Calidad) por su etiqueta
 * (Req 70.2, 70.3).
 *
 * @param estado etiqueta del estado destino; obligatoria.
 */
public record CambiarEstadoRequest(@NotBlank String estado) {
}
