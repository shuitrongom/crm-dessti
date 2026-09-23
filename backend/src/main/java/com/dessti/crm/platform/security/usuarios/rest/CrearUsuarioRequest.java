package com.dessti.crm.platform.security.usuarios.rest;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear un Usuario (Req 4.1, tarea 11.1).
 *
 * <p>El {@code tenant_id} NO se acepta en la peticion: se deriva del contexto
 * autenticado (Req 23.4). La contrasena se valida como obligatoria y se cifra en
 * el servicio; no se conserva en claro (Req 1.2, 11.3).</p>
 *
 * @param identificadorAcceso identificador de acceso (login); obligatorio.
 * @param password            contrasena en claro; obligatoria (8..255).
 * @param nombreVisible       nombre para mostrar (Req 4); opcional (max 200).
 * @param rolIds              identificadores de los Roles iniciales; al menos uno.
 */
public record CrearUsuarioRequest(
        @NotBlank @Size(max = 255) String identificadorAcceso,
        @NotBlank @Size(min = 8, max = 255) String password,
        @Size(max = 200) String nombreVisible,
        @NotEmpty Set<UUID> rolIds) {
}
