package com.dessti.crm.comercial.actividad.adapter.in.rest;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion REST de reprogramacion de una Actividad no finalizada.
 *
 * @param fechaProgramada nueva fecha programada (UTC); obligatoria.
 * @param vencimiento     nuevo vencimiento (UTC); opcional.
 */
public record ReprogramarActividadRequest(
        @NotNull(message = "La fecha programada es obligatoria.")
        Instant fechaProgramada,

        Instant vencimiento) {
}
