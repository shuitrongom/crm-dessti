package com.dessti.crm.compras.ordencompra.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de creacion de una Partida_Orden_Compra dentro de una Orden_Compra
 * (Req 31.2). Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * @param materialId     Material solicitado; obligatorio (Req 31.1).
 * @param cantidad       cantidad; entero en [1, 999,999] (Req 31.2).
 * @param precioUnitario precio unitario; en [0.01, 999,999,999.99] (Req 31.2).
 */
public record CrearPartidaOrdenCompraCommand(
        UUID materialId,
        int cantidad,
        BigDecimal precioUnitario) {
}
