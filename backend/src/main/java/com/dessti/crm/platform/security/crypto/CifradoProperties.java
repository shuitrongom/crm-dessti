package com.dessti.crm.platform.security.crypto;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades tipadas del cifrado de datos sensibles en reposo (Requisito 67).
 *
 * <p>El material criptográfico de las {@code Llave_Cifrado} se resuelve
 * <b>fuera del código fuente</b> (variables de entorno / almacén externo),
 * conforme al Req 11 y a la carga de secretos de la Tarea 6.1. La convención de
 * variables es:</p>
 * <ul>
 *   <li>{@code CRM_ENC_KEY_ACTIVE} &rarr; alias de la versión de llave activa
 *       para <b>cifrar</b> (p. ej. {@code v1}).</li>
 *   <li>{@code CRM_ENC_KEY_V1}, {@code CRM_ENC_KEY_V2}, ... &rarr; material de
 *       cada versión de llave (Base64 de 32 bytes = AES-256), disponible para
 *       <b>descifrar</b> datos previos tras una rotación.</li>
 * </ul>
 *
 * <p>Estas variables se mapean en {@code application.yml} bajo el prefijo
 * {@code crm.cifrado}:</p>
 * <pre>
 * crm:
 *   cifrado:
 *     activa: ${CRM_ENC_KEY_ACTIVE}
 *     llaves:
 *       v1: ${CRM_ENC_KEY_V1:}
 *       v2: ${CRM_ENC_KEY_V2:}
 * </pre>
 *
 * <p><b>Seguridad (Req 11.3, 67.2):</b> el valor del material de llave
 * <b>nunca</b> se escribe en logs. {@link #toString()} enmascara por completo
 * los valores y solo indica qué versiones están presentes.</p>
 *
 * @param activa alias de la versión de llave activa para cifrar (p. ej. {@code v1}).
 * @param llaves mapa {@code alias -> material Base64} con todas las versiones
 *               disponibles para descifrar.
 */
@ConfigurationProperties(prefix = "crm.cifrado")
public record CifradoProperties(
        String activa,
        Map<String, String> llaves
) {

    /**
     * Constructor compacto que normaliza el mapa de llaves a uno no nulo y con
     * orden estable por alias, para trazas deterministas.
     */
    public CifradoProperties {
        llaves = (llaves == null) ? Map.of() : new TreeMap<>(llaves);
    }

    /**
     * Representación segura que enmascara el material de las llaves (Req 11.3,
     * 67.2). Solo revela el alias activo y qué versiones están presentes.
     *
     * @return descripción sin exponer ningún valor de llave.
     */
    @Override
    public String toString() {
        return "CifradoProperties{"
                + "activa=" + (activa == null || activa.isBlank() ? "<ausente>" : activa)
                + ", versionesPresentes=" + llaves.keySet()
                + '}';
    }
}
