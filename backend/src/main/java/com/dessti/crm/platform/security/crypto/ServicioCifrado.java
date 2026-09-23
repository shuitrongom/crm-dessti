package com.dessti.crm.platform.security.crypto;

/**
 * Servicio de cifrado simétrico autenticado de datos sensibles en reposo
 * (Requisito 67).
 *
 * <p>Ofrece una API mínima de cifrado/descifrado de cadenas. La implementación
 * usa <b>AES-256-GCM</b> (cifrado autenticado, nunca ECB), con un IV/nonce
 * aleatorio por valor y soporte de <b>rotación por versión de llave</b>: el
 * texto cifrado se prefija con el alias de la versión de llave empleada
 * (p. ej. {@code v1:...}) para poder descifrar datos previos tras rotar la
 * {@code Llave_Cifrado} activa.</p>
 */
public interface ServicioCifrado {

    /**
     * Cifra un texto plano con la {@code Llave_Cifrado} <b>activa</b>.
     *
     * @param textoPlano valor a cifrar; puede ser {@code null} (devuelve {@code null}).
     * @return texto cifrado en formato {@code <alias>:<Base64(IV||ciphertext||tag)>},
     *         o {@code null} si la entrada es {@code null}.
     * @throws LlaveCifradoNoDisponibleException si no hay llave activa disponible (Req 67.6).
     * @throws CifradoException si ocurre un error criptográfico.
     */
    String cifrar(String textoPlano);

    /**
     * Descifra un texto cifrado previamente producido por {@link #cifrar(String)},
     * resolviendo la versión de llave a partir del prefijo del propio texto (lo
     * que permite descifrar datos cifrados con versiones anteriores).
     *
     * @param textoCifrado valor cifrado; puede ser {@code null} (devuelve {@code null}).
     * @return el texto plano original, o {@code null} si la entrada es {@code null}.
     * @throws LlaveCifradoNoDisponibleException si la versión de llave referida no
     *                                           está disponible (Req 67.6).
     * @throws CifradoException si el formato es inválido o la autenticación GCM falla
     *                          (dato manipulado).
     */
    String descifrar(String textoCifrado);
}
