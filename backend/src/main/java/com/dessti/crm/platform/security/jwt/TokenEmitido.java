package com.dessti.crm.platform.security.jwt;

import java.time.Instant;

/**
 * Resultado de la emision de un token JWT (Req 1).
 *
 * @param valor      cadena compacta del JWT firmado (a transmitir al cliente).
 * @param expiracion instante UTC de expiracion ({@code exp}).
 * @param jti        identificador unico del token (claim {@code jti}). Sustenta
 *                   el registro/denylist de sesiones y la revocacion de
 *                   Token_Refresco (Req 68). Puede ser {@code null} para tokens
 *                   emitidos sin identificador (por ejemplo, dobles de prueba
 *                   que solo requieren {@link #valor()} y {@link #expiracion()}).
 */
public record TokenEmitido(String valor, Instant expiracion, String jti) {

    /**
     * Constructor de conveniencia sin {@code jti}, para casos que solo requieren
     * la cadena del token y su expiracion. Equivale a {@code jti = null}.
     *
     * @param valor      cadena compacta del JWT firmado.
     * @param expiracion instante UTC de expiracion.
     */
    public TokenEmitido(String valor, Instant expiracion) {
        this(valor, expiracion, null);
    }
}
