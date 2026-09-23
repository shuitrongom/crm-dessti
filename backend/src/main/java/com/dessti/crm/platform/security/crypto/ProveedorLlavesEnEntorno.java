package com.dessti.crm.platform.security.crypto;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementación de {@link ProveedorLlaves} que resuelve el material de las
 * {@code Llave_Cifrado} desde {@link CifradoProperties}, es decir, desde
 * variables de entorno / almacén externo conforme al Req 11 (nunca embebido en
 * el código).
 *
 * <p>Convención de variables de entorno (mapeadas en {@code application.yml}):</p>
 * <ul>
 *   <li>{@code CRM_ENC_KEY_ACTIVE} &rarr; alias de la versión activa (p. ej. {@code v1}).</li>
 *   <li>{@code CRM_ENC_KEY_V1}, {@code CRM_ENC_KEY_V2}, ... &rarr; material Base64
 *       (32 bytes = AES-256) de cada versión.</li>
 * </ul>
 *
 * <p><b>Bloqueo controlado (Req 67.6):</b> si no hay llave activa configurada o
 * su material está ausente/mal formado, la construcción falla con
 * {@link LlaveCifradoNoDisponibleException}, deteniendo el arranque cuando el
 * bean se crea (coherente con el fail-fast de secretos del Req 11.2). El
 * descifrado de una versión ausente falla del mismo modo, sin exponer el valor
 * de la llave.</p>
 */
public class ProveedorLlavesEnEntorno implements ProveedorLlaves {

    /** Longitud requerida de la llave AES-256 en bytes. */
    static final int LONGITUD_LLAVE_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(ProveedorLlavesEnEntorno.class);
    private static final String ALGORITMO_LLAVE = "AES";

    private final String aliasActivo;
    private final Map<String, SecretKey> llavesPorAlias;

    /**
     * Construye el proveedor cargando y validando el material de las llaves.
     *
     * @param propiedades propiedades de cifrado resueltas desde el entorno; no nulo.
     * @throws LlaveCifradoNoDisponibleException si falta la llave activa, su
     *                                           material o alguna versión tiene
     *                                           formato/longitud inválidos (Req 67.6).
     */
    public ProveedorLlavesEnEntorno(CifradoProperties propiedades) {
        this.aliasActivo = normalizar(propiedades.activa());
        if (this.aliasActivo == null) {
            log.error("Arranque bloqueado: no hay llave de cifrado activa configurada "
                    + "(CRM_ENC_KEY_ACTIVE) (Req 67.6).");
            throw new LlaveCifradoNoDisponibleException("<activa-no-configurada>");
        }

        this.llavesPorAlias = cargarLlaves(propiedades.llaves());

        if (!this.llavesPorAlias.containsKey(this.aliasActivo)) {
            log.error("Arranque bloqueado: el material de la llave activa '{}' no está "
                    + "disponible (Req 67.6).", this.aliasActivo);
            throw new LlaveCifradoNoDisponibleException(this.aliasActivo);
        }

        // Solo se registran alias/versiones (metadatos), nunca el material (Req 67.2).
        log.info("Proveedor de llaves de cifrado inicializado. Versión activa: '{}', "
                + "versiones disponibles: {} (Req 67).", this.aliasActivo, this.llavesPorAlias.keySet());
    }

    private static Map<String, SecretKey> cargarLlaves(Map<String, String> materialPorAlias) {
        Map<String, SecretKey> resultado = new LinkedHashMap<>();
        for (Map.Entry<String, String> entrada : materialPorAlias.entrySet()) {
            String alias = normalizar(entrada.getKey());
            String materialBase64 = entrada.getValue();
            // Se omiten alias sin material (p. ej. placeholders vacíos ${CRM_ENC_KEY_V2:}).
            if (alias == null || materialBase64 == null || materialBase64.isBlank()) {
                continue;
            }
            resultado.put(alias, decodificarLlave(alias, materialBase64.trim()));
        }
        return resultado;
    }

    private static SecretKey decodificarLlave(String alias, String materialBase64) {
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(materialBase64);
        } catch (IllegalArgumentException e) {
            // No se incluye el material en el mensaje (Req 67.2).
            throw new LlaveCifradoNoDisponibleException(
                    "Material de la llave de cifrado '" + alias + "' con formato Base64 inválido "
                            + "(Req 67.6). No se expone su valor.", e);
        }
        if (bytes.length != LONGITUD_LLAVE_BYTES) {
            throw new LlaveCifradoNoDisponibleException(
                    "Material de la llave de cifrado '" + alias + "' con longitud inválida: se "
                            + "requieren " + LONGITUD_LLAVE_BYTES + " bytes (AES-256) (Req 67.6). "
                            + "No se expone su valor.");
        }
        return new SecretKeySpec(bytes, ALGORITMO_LLAVE);
    }

    @Override
    public String aliasActivo() {
        return aliasActivo;
    }

    @Override
    public SecretKey llavePara(String alias) {
        SecretKey llave = llavesPorAlias.get(normalizar(alias));
        if (llave == null) {
            throw new LlaveCifradoNoDisponibleException(alias);
        }
        return llave;
    }

    @Override
    public Set<String> versionesDisponibles() {
        return Set.copyOf(llavesPorAlias.keySet());
    }

    private static String normalizar(String alias) {
        if (alias == null) {
            return null;
        }
        String limpio = alias.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
