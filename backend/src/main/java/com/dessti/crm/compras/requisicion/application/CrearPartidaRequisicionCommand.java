package com.dessti.crm.compras.requisicion.application;

import java.util.UUID;

/**
 * Comando de creacion de una Partida_Requisicion dentro de una Requisicion_Compra
 * (Req 30.1). Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * @param materialId Material solicitado; obligatorio (Req 30.1).
 * @param cantidad   cantidad; entero en [1, 999,999] (Req 30.1).
 */
public record CrearPartidaRequisicionCommand(
        UUID materialId,
        int cantidad) {
}
