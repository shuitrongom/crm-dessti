package com.dessti.crm.platform.respaldo.application;

import java.nio.file.Path;

/**
 * Puerto de cifrado de artefactos de respaldo (Req 50.3, 67). Cifra el volcado
 * en claro produciendo un artefacto cifrado en reposo, y lo descifra para la
 * restauracion, usando la {@code Llave_Cifrado} <strong>versionada</strong>
 * existente (AES-256-GCM), de modo que un artefacto cifrado con una version
 * previa siga siendo descifrable tras una rotacion (Req 67).
 *
 * <p>El material de la llave NUNCA se expone (Req 67.2).</p>
 */
public interface CifradorRespaldoPort {

    /**
     * Cifra el volcado en claro y escribe el artefacto cifrado en la ruta
     * destino.
     *
     * @param volcadoEnClaro    ruta del volcado en claro de origen; no nula.
     * @param destinoCifrado    ruta del artefacto cifrado a producir; no nula.
     * @return metadatos del artefacto cifrado (alias de llave, checksum, tamano).
     * @throws RespaldoException si el cifrado falla.
     */
    ArtefactoCifrado cifrar(Path volcadoEnClaro, Path destinoCifrado);

    /**
     * Descifra un artefacto de respaldo hacia un volcado en claro para la
     * restauracion (Req 50.2). Verifica la integridad autenticada (GCM); un
     * artefacto manipulado o cifrado con una version de llave no disponible
     * provoca un fallo controlado sin exponer la llave (Req 67.6).
     *
     * @param artefactoCifrado ruta del artefacto cifrado de origen; no nula.
     * @param destinoEnClaro   ruta del volcado en claro a producir; no nula.
     * @throws RespaldoException si el descifrado falla o el artefacto es invalido.
     */
    void descifrar(Path artefactoCifrado, Path destinoEnClaro);

    /**
     * Metadatos del artefacto cifrado producidos por {@link #cifrar(Path, Path)}.
     *
     * @param aliasLlave  alias/version de la Llave_Cifrado usada (sin material).
     * @param checksum    hash SHA-256 (hex) del artefacto cifrado.
     * @param tamanoBytes tamano del artefacto cifrado en bytes.
     */
    record ArtefactoCifrado(String aliasLlave, String checksum, long tamanoBytes) {
    }
}
