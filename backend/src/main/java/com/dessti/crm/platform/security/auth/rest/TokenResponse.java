package com.dessti.crm.platform.security.auth.rest;

/**
 * Respuesta con los tokens emitidos tras un login o refresh exitoso (Req 1.4,
 * 1.5, 1.7). DTO distinto de las entidades de persistencia.
 *
 * @param accessToken   Token_Acceso (JWT) de vida corta.
 * @param refreshToken  Token_Refresco (JWT) de vida mas larga. En el refresh
 *                      puede reemitirse el mismo par o solo el acceso; en esta
 *                      tarea el login devuelve ambos y el refresh reemite el
 *                      Token_Acceso (y reutiliza el refresco vigente).
 * @param tokenType     esquema de autenticacion; siempre {@code "Bearer"}.
 * @param expiresIn     segundos de vigencia restante del Token_Acceso.
 * @param debeCambiarPassword {@code true} cuando la cuenta debe cambiar su
 *                      contrasena antes de operar (contrasena temporal, V69);
 *                      el cliente debe forzar el cambio tras el login.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean debeCambiarPassword
) {

    public static final String TIPO_BEARER = "Bearer";
}
