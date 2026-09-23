package com.dessti.crm.compras.requisicion.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de una Partida_Requisicion en las peticiones REST (Req 30.1). DTO de
 * entrada del contrato, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.compras.requisicion.application.CrearPartidaRequisicionCommand}
 * (Req 12.2).
 *
 * <p>La validacion de campo (Bean Validation -&gt; 400) cubre presencia y rango; la
 * existencia del Material (404) la verifica la capa de aplicacion.</p>
 *
 * @param materialId Material solicitado; obligatorio (Req 30.1).
 * @param cantidad   cantidad; entero en [1, 999,999] (Req 30.1).
 */
public record PartidaRequisicionRequest(
        @NotNull UUID materialId,
        @Min(1) @Max(999999) int cantidad) {
}
