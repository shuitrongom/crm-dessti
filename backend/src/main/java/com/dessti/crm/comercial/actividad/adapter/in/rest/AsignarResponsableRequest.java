package com.dessti.crm.comercial.actividad.adapter.in.rest;

import java.util.UUID;

/**
 * Cuerpo de la peticion REST de asignacion del responsable de una Actividad. Un
 * {@code usuarioId} nulo desasigna al responsable.
 *
 * @param usuarioId identificador del Usuario responsable; {@code null} para desasignar.
 */
public record AsignarResponsableRequest(
        UUID usuarioId) {
}
