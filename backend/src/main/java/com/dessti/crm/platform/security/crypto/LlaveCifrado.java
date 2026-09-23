package com.dessti.crm.platform.security.crypto;

/**
 * Metadatos de una {@code Llave_Cifrado} (Requisito 67.2, 67.3).
 *
 * <p><b>Solo metadatos</b>: este objeto describe una versión de llave por su
 * {@code alias} y su {@code estado}, y <b>nunca</b> contiene el material
 * criptográfico. El material se resuelve fuera del código (Req 11) y vive
 * únicamente en memoria dentro del proveedor de llaves; no se persiste en claro
 * ni se escribe en logs.</p>
 *
 * <p>El estado habilita la <b>rotación</b>: existe a lo sumo una llave
 * {@link Estado#ACTIVA} (la que cifra los datos nuevos), mientras que las
 * llaves {@link Estado#ROTADA} se conservan disponibles para <b>descifrar</b>
 * datos cifrados con versiones anteriores.</p>
 *
 * @param alias  identificador de versión de la llave (p. ej. {@code v1}).
 * @param estado estado de la llave dentro del ciclo de rotación.
 */
public record LlaveCifrado(String alias, Estado estado) {

    /**
     * Estado de una {@code Llave_Cifrado} dentro de su ciclo de vida de rotación.
     */
    public enum Estado {
        /** Llave vigente para cifrar datos nuevos (a lo sumo una). */
        ACTIVA,
        /** Llave anterior conservada únicamente para descifrar datos previos. */
        ROTADA,
        /** Llave dada de baja; no debe usarse para cifrar ni descifrar. */
        REVOCADA
    }
}
