package com.dessti.crm.platform.security.crypto;

/**
 * Excepción de una operación de cifrado/descifrado que falla por una causa
 * criptográfica (formato inválido, etiqueta de autenticación GCM incorrecta,
 * dato manipulado, etc.).
 *
 * <p>El mensaje <b>nunca</b> incluye el texto plano ni el material de la llave
 * (Req 67.2), evitando filtraciones de datos sensibles en logs.</p>
 */
public class CifradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param mensaje descripción del error (sin datos sensibles ni material de llave).
     * @param causa   causa criptográfica subyacente.
     */
    public CifradoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    /**
     * @param mensaje descripción del error (sin datos sensibles ni material de llave).
     */
    public CifradoException(String mensaje) {
        super(mensaje);
    }
}
