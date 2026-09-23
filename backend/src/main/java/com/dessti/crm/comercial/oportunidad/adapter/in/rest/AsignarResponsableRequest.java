package com.dessti.crm.comercial.oportunidad.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para asignar el Usuario de ventas responsable de una
 * Oportunidad (Req 14.2). DTO de entrada del contrato REST.
 *
 * @param usuarioId identificador del Usuario responsable; obligatorio.
 */
public record AsignarResponsableRequest(@NotNull UUID usuarioId) {
}
