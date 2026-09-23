package com.dessti.crm.estrategia.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para actualizar el valor actual de un resultado clave, que
 * recalcula el avance ponderado del objetivo (Req 58.8). La validacion de rango
 * (valor actual &gt;= 0) la aplica el dominio.
 *
 * @param valorActual nuevo valor actual medido; obligatorio (Req 58.8).
 */
public record ActualizarValorResultadoClaveRequest(@NotNull BigDecimal valorActual) {
}
