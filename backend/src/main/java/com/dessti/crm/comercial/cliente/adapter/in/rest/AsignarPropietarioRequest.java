package com.dessti.crm.comercial.cliente.adapter.in.rest;

import java.util.UUID;

/**
 * Cuerpo de la peticion REST para asignar el Usuario propietario/vendedor de un
 * Cliente (V81). Un {@code usuarioId} nulo desasigna al propietario.
 *
 * @param usuarioId identificador del Usuario propietario; {@code null} para desasignar.
 */
public record AsignarPropietarioRequest(
        UUID usuarioId) {
}
