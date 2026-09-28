package com.dessti.crm.comercial.oportunidad.adapter.in.rest;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cuerpo de la peticion para ajustar el forecast de una Oportunidad (V81): la
 * probabilidad de cierre (0..100) y la fecha esperada de cierre. Ambos campos son
 * opcionales: una {@code probabilidad} nula deja la actual sin cambio y una
 * {@code fechaCierreEsperada} nula la deja sin estimar.
 *
 * @param probabilidad        probabilidad de cierre en porcentaje [0,100]; opcional.
 * @param fechaCierreEsperada fecha esperada de cierre (ISO date); opcional.
 */
public record AjustarForecastRequest(
        @Min(0) @Max(100) Integer probabilidad,
        LocalDate fechaCierreEsperada) {
}
