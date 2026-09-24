package com.dessti.crm.contabilidad.electronica.domain.xml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.dessti.crm.contabilidad.electronica.domain.modelo.CuentaCatalogoSat;

/**
 * Generador PURO del <strong>Catalogo de Cuentas XML</strong> de la Contabilidad
 * Electronica del SAT (Anexo 24), conforme al esquema
 * {@code www.sat.gob.mx/esquemas/ContabilidadE/1_3/CatalogoCuentas} (Req 2).
 *
 * <p>Sin dependencias de Spring ni de JPA: recibe el {@link EncabezadoSat} y la
 * lista de {@link CuentaCatalogoSat} y devuelve el XML como {@code String} (UTF-8).
 * Usa StAX ({@link XMLStreamWriter}) para garantizar XML bien formado y el escape
 * correcto de caracteres. El orden de las cuentas lo determina el llamador (debe
 * pasarlas ya ordenadas de forma determinista, p. ej. por NumCta).</p>
 */
public final class GeneradorCatalogoXml {

    private static final String NS_CATALOGO =
            "www.sat.gob.mx/esquemas/ContabilidadE/1_3/CatalogoCuentas";
    private static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String SCHEMA_LOCATION =
            "www.sat.gob.mx/esquemas/ContabilidadE/1_3/CatalogoCuentas "
                    + "http://www.sat.gob.mx/esquemas/ContabilidadE/1_3/CatalogoCuentas/CatalogoCuentas_1_3.xsd";
    private static final String PREFIJO = "catalogocuentas";

    private GeneradorCatalogoXml() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Genera el Catalogo_XML del SAT.
     *
     * @param encabezado datos de encabezado (version, RFC, anio, mes).
     * @param cuentas    cuentas a emitir (ya ordenadas de forma determinista).
     * @return el XML del catalogo de cuentas (UTF-8).
     * @throws IllegalStateException si ocurre un error de serializacion XML.
     */
    public static String generar(EncabezadoSat encabezado, List<CuentaCatalogoSat> cuentas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            XMLStreamWriter w = XMLOutputFactory.newFactory()
                    .createXMLStreamWriter(salida, StandardCharsets.UTF_8.name());
            w.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");

            w.setPrefix(PREFIJO, NS_CATALOGO);
            w.writeStartElement(NS_CATALOGO, "Catalogo");
            w.writeNamespace(PREFIJO, NS_CATALOGO);
            w.writeNamespace("xsi", NS_XSI);
            w.writeAttribute(NS_XSI, "schemaLocation", SCHEMA_LOCATION);
            w.writeAttribute("Version", encabezado.version());
            w.writeAttribute("RFC", encabezado.rfc());
            w.writeAttribute("Mes", encabezado.mes());
            w.writeAttribute("Anio", Integer.toString(encabezado.anio()));

            for (CuentaCatalogoSat cuenta : cuentas) {
                w.writeStartElement(NS_CATALOGO, "Ctas");
                w.writeAttribute("CodAgrup", nvl(cuenta.codAgrup()));
                w.writeAttribute("NumCta", nvl(cuenta.numCta()));
                w.writeAttribute("Desc", nvl(cuenta.desc()));
                w.writeAttribute("Nivel", Integer.toString(cuenta.nivel()));
                w.writeAttribute("Natur", nvl(cuenta.natur()));
                w.writeEndElement();
            }

            w.writeEndElement();
            w.writeEndDocument();
            w.flush();
            w.close();
        } catch (XMLStreamException ex) {
            throw new IllegalStateException("No se pudo generar el Catalogo_XML del SAT.", ex);
        }
        return salida.toString(StandardCharsets.UTF_8);
    }

    private static String nvl(String v) {
        return (v == null) ? "" : v;
    }
}
