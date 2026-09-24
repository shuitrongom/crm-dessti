package com.dessti.crm.contabilidad.electronica.application;

import java.nio.charset.StandardCharsets;

/**
 * Resultado de una exportacion XML de Contabilidad Electronica: el nombre de archivo
 * conforme a la convencion del SAT y su contenido.
 *
 * @param nombreArchivo nombre del archivo (p. ej. {@code XAXX010101000202609CT.xml}).
 * @param contenidoXml  contenido del XML (UTF-8).
 */
public record ArchivoXmlDto(String nombreArchivo, String contenidoXml) {

    /**
     * @return el contenido del XML codificado en UTF-8, listo para descarga.
     */
    public byte[] bytes() {
        return contenidoXml.getBytes(StandardCharsets.UTF_8);
    }
}
