package com.dessti.crm.platform.security.perfil.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para el cambio de la contrasena propia (CHANGE 2):
 * {@code PUT /auth/perfil/password}.
 *
 * <p>La {@code passwordActual} es obligatoria (se verifica contra el hash
 * almacenado). La {@code passwordNueva} es obligatoria y debe tener entre 8 y
 * 255 caracteres, igual que en el resto del Sistema (alta de Usuarios). Una
 * contrasena nueva fuera de rango produce 400 (validacion de Bean Validation).</p>
 *
 * <p><strong>Secretos (Req 11.3):</strong> ambos valores son contrasenas en
 * claro; nunca se registran en logs ni se devuelven en las respuestas.</p>
 *
 * @param passwordActual contrasena actual en claro; obligatoria.
 * @param passwordNueva  nueva contrasena en claro; obligatoria (8..255).
 */
public record CambiarPasswordRequest(
        @NotBlank String passwordActual,
        @NotBlank @Size(min = 8, max = 255) String passwordNueva) {
}
