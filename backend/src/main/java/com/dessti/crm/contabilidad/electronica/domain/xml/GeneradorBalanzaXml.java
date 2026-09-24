package com.dessti.crm.contabilidad.electronica.domain.xml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.dessti.crm.contabilidad.electronica.domain.modelo.RenglonBalanzaSat;

/**
 * Generador PURO de la <strong>Balanza de Comprobacion XML</strong> de la
 * Contabilidad Electronica del SAT (Anexo 24), conforme al esquema
 * {@code www.sat.gob.mx/esquemas/ContabilidadE/1_3/BalanzaComprobacion} (Req 3).
 *
 * <p>Sin dependencias de Spring ni de JPA. Usa StAX y formatea los importes a dos
 * decimales con {@link XmlUtil#importe}. El orden de los renglones lo determina el
 * llamador.</p>
 */
public final class GeneradorBalanzaXml {

    private static final String NS_BALANZA =
            "www.sat.gob.mx/esquemas/ContabilidadE/1_3/BalanzaComprobacion";
    private static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String SCHEMA_LOCATION =
            "www.sat.gob.mx/esquemas/ContabilidadE/1_3/BalanzaComprobacion "
                    + "http://www.sat.gob.mx/esquemas/ContabilidadE/1_3/BalanzaComprobacion/BalanzaComprobacion_1_3.xsd";
    private static final String PREFIJO = "BCE";

    private GeneradorBalanzaXml() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Genera la Balanza_XML del SAT.
     *
     * @param encabezado datos de encabezado (version, RFC, anio, mes).
     * @param tipoEnvio  tipo de envio: {@code N} (Normal) o {@code C} (Complementaria).
     * @param renglones  renglones por cuenta (ya ordenados de forma determinista).
     * @return el XML de la balanza de comprobacion (UTF-8).
     * @throws IllegalStateException si ocurre un error de serializacion XML.
     */
    public static String generar(EncabezadoSat encabezado, String tipoEnvio,
                                 List<RenglonBalanzaSat> renglones) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            XMLStreamWriter w = XMLOutputFactory.newFactory()
                    .createXMLStreamWriter(salida, StandardCharsets.UTF_8.name());
            w.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");

            w.setPrefix(PREFIJO, NS_BALANZA);
            w.writeStartElement(NS_BALANZA, "Balanza");
            w.writeNamespace(PREFIJO, NS_BALANZA);
            w.writeNamespace("xsi", NS_XSI);
            w.writeAttribute(NS_XSI, "schemaLocation", SCHEMA_LOCATION);
            w.writeAttribute("Version", encabezado.version());
            w.writeAttribute("RFC", encabezado.rfc());
            w.writeAttribute("Mes", encabezado.mes());
            w.writeAttribute("Anio", Integer.toString(encabezado.anio()));
            w.writeAttribute("TipoEnvio", tipoEnvio);

            for (RenglonBalanzaSat r : renglones) {
                w.writeStartElement(NS_BALANZA, "Ctas");
                w.writeAttribute("NumCta", nvl(r.numCta()));
                w.writeAttribute("SaldoIni", XmlUtil.importe(r.saldoIni()));
                w.writeAttribute("Debe", XmlUtil.importe(r.debe()));
                w.writeAttribute("Haber", XmlUtil.importe(r.haber()));
                w.writeAttribute("SaldoFin", XmlUtil.importe(r.saldoFin()));
                w.writeEndElement();
            }

            w.writeEndElement();
            w.writeEndDocument();
            w.flush();
            w.close();
        } catch (XMLStreamException ex) {
            throw new IllegalStateException("No se pudo generar la Balanza_XML del SAT.", ex);
        }
        return salida.toString(StandardCharsets.UTF_8);
    }

    private static String nvl(String v) {
        return (v == null) ? "" : v;
    }
}
