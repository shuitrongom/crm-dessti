package com.dessti.crm.comercial.cotizacion.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para cambiar el estado de una Cotizacion (Req 6.6). DTO de
 * entrada del contrato REST. La etiqueta se valida y traduce a
 * {@link com.dessti.crm.comercial.cotizacion.domain.EstadoCotizacion} en la capa
 * de aplicacion; una transicion no permitida se rechaza con 409.
 *
 * @param estado etiqueta del estado destino ({@code borrador}, {@code enviada},
 *               {@code aprobada}, {@code rechazada}); obligatoria.
 */
public record CambiarEstadoRequest(@NotBlank String estado) {
}
