package com.dessti.crm.comercial.oportunidad.adapter.in.rest;

import java.util.UUID;

/**
 * Cuerpo de respuesta de la conversion de una Oportunidad ganada en Cotizacion
 * (Req 14.5): devuelve el identificador de la Cotizacion creada.
 *
 * @param cotizacionId identificador de la Cotizacion generada.
 */
public record ConversionCotizacionResponse(UUID cotizacionId) {
}
