package com.dessti.crm.compras.requisicion.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para cambiar el estado de una Requisicion_Compra (Req 30.2).
 * DTO de entrada del contrato REST. La etiqueta se valida y traduce a
 * {@link com.dessti.crm.compras.requisicion.domain.EstadoRequisicionCompra} en la
 * capa de aplicacion; una transicion no permitida se rechaza con 409.
 *
 * @param estado etiqueta del estado destino ({@code borrador}, {@code enviada},
 *               {@code aprobada}, {@code rechazada}, {@code cancelada});
 *               obligatoria.
 */
public record CambiarEstadoRequisicionRequest(@NotBlank String estado) {
}
