package com.dessti.crm.estrategia.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para actualizar el avance de un objetivo sin resultados
 * clave (Req 58.4, 58.9). El avance se acota a [0, 100] en el dominio (Req 58.9).
 *
 * @param avance nuevo avance; obligatorio (se acota a [0, 100], Req 58.9).
 */
public record ActualizarAvanceRequest(@NotNull BigDecimal avance) {
}
