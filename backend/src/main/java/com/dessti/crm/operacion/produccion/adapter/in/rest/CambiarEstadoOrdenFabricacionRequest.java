package com.dessti.crm.operacion.produccion.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para cambiar el estado de una Orden_Fabricacion (Req 7.5).
 * DTO de entrada del contrato REST. La etiqueta se valida y traduce a
 * {@link com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion}
 * en la capa de aplicacion; una transicion no permitida se rechaza con 409
 * (Req 7.6).
 *
 * @param estado etiqueta del estado destino ({@code pendiente}, {@code en_produccion},
 *               {@code terminada}, {@code cancelada}); obligatoria.
 */
public record CambiarEstadoOrdenFabricacionRequest(@NotBlank String estado) {
}
