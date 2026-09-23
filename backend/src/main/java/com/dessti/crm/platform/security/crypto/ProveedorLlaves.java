package com.dessti.crm.platform.security.crypto;

import java.util.Set;
import javax.crypto.SecretKey;

/**
 * Proveedor de {@code Llave_Cifrado} en memoria (Requisito 67).
 *
 * <p>Abstrae el origen del material criptográfico (variables de entorno /
 * almacén externo, Req 11) y expone las llaves por su alias/versión, habilitando
 * la <b>rotación</b>: se cifra siempre con la versión {@link #aliasActivo()} y
 * se puede descifrar con cualquier versión disponible.</p>
 *
 * <p>Ninguna implementación debe registrar el material de las llaves en logs
 * (Req 67.2).</p>
 */
public interface ProveedorLlaves {

    /**
     * @return alias/versión de la llave <b>activa</b> con la que se cifran los
     *         datos nuevos (p. ej. {@code v1}).
     * @throws LlaveCifradoNoDisponibleException si no hay ninguna llave activa
     *                                           configurada (Req 67.6).
     */
    String aliasActivo();

    /**
     * Devuelve la llave AES asociada a un alias/versión, para descifrar datos
     * previos o cifrar con la versión activa.
     *
     * @param alias alias/versión de la llave (p. ej. {@code v1}); no nulo.
     * @return la {@link SecretKey} AES correspondiente.
     * @throws LlaveCifradoNoDisponibleException si la versión solicitada no está
     *                                           disponible (Req 67.6).
     */
    SecretKey llavePara(String alias);

    /**
     * @return conjunto de alias/versiones disponibles (solo metadatos, sin
     *         material).
     */
    Set<String> versionesDisponibles();
}
