package com.dessti.crm.compras.ordencompra.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para cambiar el estado de una Orden_Compra (Req 31.5). DTO
 * de entrada del contrato REST. La etiqueta se valida y traduce a
 * {@link com.dessti.crm.compras.ordencompra.domain.EstadoOrdenCompra} en la capa
 * de aplicacion; una transicion no permitida se rechaza con 409.
 *
 * @param estado etiqueta del estado destino ({@code abierta},
 *               {@code recibida_parcial}, {@code recibida_total}, {@code cerrada},
 *               {@code cancelada}); obligatoria.
 */
public record CambiarEstadoOrdenCompraRequest(@NotBlank String estado) {
}
