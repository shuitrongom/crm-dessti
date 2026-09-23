package com.dessti.crm.platform.security.auth.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos de entrada del inicio de sesion (Req 1.1). DTO distinto de las
 * entidades de persistencia.
 *
 * @param identificador identificador de acceso del Usuario.
 * @param password      contrasena en claro (se verifica contra el hash BCrypt).
 */
public record LoginRequest(
        @NotBlank(message = "El identificador es obligatorio")
        String identificador,

        @NotBlank(message = "La contrasena es obligatoria")
        String password
) {
}
