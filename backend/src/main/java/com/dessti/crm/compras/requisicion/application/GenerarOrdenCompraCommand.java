package com.dessti.crm.compras.requisicion.application;

import java.util.List;
import java.util.UUID;

import com.dessti.crm.compras.ordencompra.application.CrearPartidaOrdenCompraCommand;

/**
 * Comando para generar una Orden_Compra a partir de una Requisicion_Compra
 * aprobada (Req 30.3). Objeto de entrada de la capa de aplicacion.
 *
 * <p>Una Requisicion_Compra registra los Materiales y cantidades solicitados, pero
 * no el Proveedor ni los precios; por ello la generacion de la Orden_Compra aporta
 * el {@code proveedorId} y las partidas con precio (Req 31.1, 31.2). La
 * precondicion "la requisicion esta aprobada" (Req 30.3) la verifica
 * {@code ServicioRequisiciones} antes de crear la Orden.</p>
 *
 * @param proveedorId Proveedor existente al que se asocia la Orden_Compra;
 *                    obligatorio (Req 31.1).
 * @param partidas    partidas de la Orden_Compra con Material, cantidad y precio;
 *                    entre 1 y 500 (Req 31.1, 31.2).
 */
public record GenerarOrdenCompraCommand(
        UUID proveedorId,
        List<CrearPartidaOrdenCompraCommand> partidas) {
}
