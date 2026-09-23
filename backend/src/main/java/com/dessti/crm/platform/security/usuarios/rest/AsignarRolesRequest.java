package com.dessti.crm.platform.security.usuarios.rest;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/**
 * Cuerpo de la peticion para reemplazar los Roles de un Usuario (Req 4.3,
 * tarea 11.1).
 *
 * @param rolIds identificadores de los Roles a asignar; al menos uno.
 */
public record AsignarRolesRequest(@NotEmpty Set<UUID> rolIds) {
}
