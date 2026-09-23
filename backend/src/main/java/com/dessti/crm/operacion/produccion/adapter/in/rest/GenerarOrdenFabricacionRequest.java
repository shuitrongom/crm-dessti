package com.dessti.crm.operacion.produccion.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para generar una Orden_Fabricacion a partir de una
 * Cotizacion aprobada (Req 7.1). DTO de entrada del contrato REST, distinto de la
 * entidad JPA. Las precondiciones (Cotizacion aprobada, sin OF previa, con
 * Prueba_Diseno aprobada; Property 7) las aplica la capa de aplicacion.
 *
 * @param cotizacionId identificador de la Cotizacion aprobada de origen; obligatorio.
 */
public record GenerarOrdenFabricacionRequest(@NotNull UUID cotizacionId) {
}
