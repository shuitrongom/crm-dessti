package com.dessti.crm.calidad.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para vincular -sin obligar- una Queja_Cliente a una
 * Accion_Correctiva (Req 70.1, clausula 10.2).
 *
 * @param accionCorrectivaId Accion_Correctiva a vincular; obligatorio.
 */
public record VincularAccionCorrectivaRequest(@NotNull UUID accionCorrectivaId) {
}
