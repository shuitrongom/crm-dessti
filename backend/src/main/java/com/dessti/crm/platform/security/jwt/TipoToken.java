package com.dessti.crm.platform.security.jwt;

/**
 * Tipo de token JWT emitido por el Sistema (Req 1).
 *
 * <p>Se materializa en el claim {@code typ} del JWT para distinguir un
 * {@code Token_Acceso} (de vida corta, usado en peticiones protegidas) de un
 * {@code Token_Refresco} (de vida mas larga, usado solo para renovar acceso).
 * Esta separacion evita que un Token_Refresco se acepte como Token_Acceso o
 * viceversa.</p>
 */
public enum TipoToken {

    /** Token de acceso de vida corta (<= 15 min, Req 1.4). */
    ACCESO,

    /** Token de refresco de vida mas larga (<= 7 dias, Req 1.7). */
    REFRESCO
}
