package com.dessti.crm.platform.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Implementación de {@link ServicioCifrado} basada en <b>AES-256-GCM</b>
 * (cifrado autenticado) para proteger datos sensibles en reposo (Requisito 67).
 *
 * <p><b>Algoritmo:</b> {@code AES/GCM/NoPadding} con llave de 256 bits. Se usa
 * un IV/nonce de 12 bytes generado con {@link SecureRandom} <b>por cada</b>
 * operación de cifrado (nunca se reutiliza), y una etiqueta de autenticación
 * (tag) de 128 bits que garantiza integridad: cualquier manipulación del dato
 * cifrado se detecta al descifrar.</p>
 *
 * <p><b>Formato del texto cifrado:</b>
 * {@code <aliasLlave>:<Base64(IV || ciphertext || tag)>}. El prefijo con el
 * alias de versión de llave habilita la <b>rotación</b>: al rotar la llave
 * activa a una nueva versión, los datos previos siguen siendo descifrables
 * porque su prefijo indica con qué versión fueron cifrados.</p>
 *
 * <p><b>Seguridad:</b> nunca se registran en logs ni el texto plano ni el
 * material de la llave (Req 67.2). El proveedor de llaves resuelve el material
 * fuera del código (Req 11).</p>
 */
public class ServicioCifradoAesGcm implements ServicioCifrado {

    /** Transformación AES-GCM sin relleno (cifrado autenticado). */
    static final String TRANSFORMACION = "AES/GCM/NoPadding";
    /** Longitud del IV/nonce recomendada para GCM, en bytes. */
    static final int LONGITUD_IV_BYTES = 12;
    /** Longitud de la etiqueta de autenticación GCM, en bits. */
    static final int LONGITUD_TAG_BITS = 128;
    /** Separador entre el alias de versión de llave y el contenido cifrado. */
    static final char SEPARADOR = ':';

    private final ProveedorLlaves proveedorLlaves;
    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * @param proveedorLlaves proveedor que resuelve las {@code Llave_Cifrado}
     *                        (activa para cifrar, cualquier versión para
     *                        descifrar); no nulo.
     */
    public ServicioCifradoAesGcm(ProveedorLlaves proveedorLlaves) {
        this.proveedorLlaves = proveedorLlaves;
    }

    @Override
    public String cifrar(String textoPlano) {
        if (textoPlano == null) {
            return null;
        }
        String alias = proveedorLlaves.aliasActivo();
        SecretKey llave = proveedorLlaves.llavePara(alias);

        byte[] iv = new byte[LONGITUD_IV_BYTES];
        aleatorio.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(LONGITUD_TAG_BITS, iv));
            byte[] cifradoConTag = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));

            // Empaquetado: IV || (ciphertext || tag) en un solo bloque.
            byte[] empaquetado = new byte[iv.length + cifradoConTag.length];
            System.arraycopy(iv, 0, empaquetado, 0, iv.length);
            System.arraycopy(cifradoConTag, 0, empaquetado, iv.length, cifradoConTag.length);

            return alias + SEPARADOR + Base64.getEncoder().encodeToString(empaquetado);
        } catch (GeneralSecurityException e) {
            // No se incluye el texto plano en el mensaje (Req 67.2).
            throw new CifradoException("Fallo al cifrar el dato sensible (Req 67).", e);
        }
    }

    @Override
    public String descifrar(String textoCifrado) {
        if (textoCifrado == null) {
            return null;
        }
        int posicionSeparador = textoCifrado.indexOf(SEPARADOR);
        if (posicionSeparador <= 0) {
            throw new CifradoException(
                    "Formato de texto cifrado inválido: falta el prefijo de versión de llave (Req 67).");
        }

        String alias = textoCifrado.substring(0, posicionSeparador);
        String contenidoBase64 = textoCifrado.substring(posicionSeparador + 1);

        SecretKey llave = proveedorLlaves.llavePara(alias);

        final byte[] empaquetado;
        try {
            empaquetado = Base64.getDecoder().decode(contenidoBase64);
        } catch (IllegalArgumentException e) {
            throw new CifradoException("Formato de texto cifrado inválido: Base64 corrupto (Req 67).", e);
        }
        if (empaquetado.length <= LONGITUD_IV_BYTES) {
            throw new CifradoException("Texto cifrado inválido: longitud insuficiente (Req 67).");
        }

        byte[] iv = new byte[LONGITUD_IV_BYTES];
        byte[] cifradoConTag = new byte[empaquetado.length - LONGITUD_IV_BYTES];
        System.arraycopy(empaquetado, 0, iv, 0, LONGITUD_IV_BYTES);
        System.arraycopy(empaquetado, LONGITUD_IV_BYTES, cifradoConTag, 0, cifradoConTag.length);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(LONGITUD_TAG_BITS, iv));
            byte[] textoPlano = cipher.doFinal(cifradoConTag);
            return new String(textoPlano, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Incluye el caso de etiqueta GCM inválida (dato manipulado); sin exponer datos.
            throw new CifradoException(
                    "Fallo al descifrar el dato sensible: contenido inválido o manipulado (Req 67).", e);
        }
    }
}
