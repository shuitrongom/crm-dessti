package com.dessti.crm.compras.requisicion.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import com.dessti.crm.compras.ordencompra.adapter.in.rest.PartidaOrdenCompraRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para generar una Orden_Compra a partir de una
 * Requisicion_Compra aprobada (Req 30.3). DTO de entrada del contrato REST.
 *
 * <p>La Requisicion_Compra aporta los Materiales y cantidades solicitados; la
 * generacion de la Orden_Compra requiere ademas el Proveedor y el precio de cada
 * partida (Req 31.1, 31.2), que se envian aqui. La precondicion "la requisicion
 * esta aprobada" (Req 30.3) la aplica la capa de aplicacion (422 en caso contrario).
 * Se reutiliza {@link PartidaOrdenCompraRequest} del submodulo de Ordenes de Compra
 * para las partidas.</p>
 *
 * @param proveedorId Proveedor existente al que se asocia la Orden; obligatorio.
 * @param partidas    partidas de la Orden_Compra (Material, cantidad, precio); entre
 *                    1 y 500 (Req 31.1).
 */
public record GenerarOrdenCompraRequest(
        @NotNull UUID proveedorId,
        @NotEmpty @Size(max = 500) @Valid List<PartidaOrdenCompraRequest> partidas) {
}
