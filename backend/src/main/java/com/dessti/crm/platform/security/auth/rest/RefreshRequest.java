package com.dessti.crm.platform.security.auth.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos de entrada de la renovacion de acceso (Req 1.5). DTO distinto de las
 * entidades de persistencia.
 *
 * @param refreshToken Token_Refresco vigente presentado por el cliente.
 */
public record RefreshRequest(
        @NotBlank(message = "El token de refresco es obligatorio")
        String refreshToken
) {
}
