package com.dessti.crm.social.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para asignar/transferir una Conversacion a un Usuario
 * (handover, Req 64.10). DTO de entrada del contrato REST.
 *
 * @param usuarioId identificador del Usuario responsable del handover; obligatorio.
 */
public record AsignarConversacionRequest(@NotNull UUID usuarioId) {
}
