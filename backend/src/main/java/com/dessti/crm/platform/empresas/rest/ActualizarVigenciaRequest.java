package com.dessti.crm.platform.empresas.rest;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para actualizar la vigencia de una Suscripcion
 * (Req 25.2, tarea 14.2).
 *
 * @param vigenciaInicio nuevo inicio de vigencia; obligatorio.
 * @param vigenciaFin    nuevo fin de vigencia; opcional ({@code null} = sin fin).
 */
public record ActualizarVigenciaRequest(
        @NotNull LocalDate vigenciaInicio,
        LocalDate vigenciaFin) {
}
