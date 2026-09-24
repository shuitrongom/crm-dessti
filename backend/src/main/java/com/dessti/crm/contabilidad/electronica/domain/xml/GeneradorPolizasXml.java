package com.dessti.crm.contabilidad.electronica.domain.xml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.dessti.crm.contabilidad.electronica.domain.modelo.PolizaSat;
import com.dessti.crm.contabilidad.electronica.domain.modelo.TransaccionSat;

/**
 * Generador PURO de las <strong>Polizas del Periodo XML</strong> de la Contabilidad
 * Electronica del SAT (Anexo 24), conforme al esquema
 * {@code www.sat.gob.mx/esquemas/ContabilidadE/1_3/PolizasPeriodo} (Req 4).
 *
 * <p>Sin dependencias de Spring ni de JPA. Usa StAX; los importes van a dos
 * decimales y las fechas en formato {@code AAAA-MM-DD}. El orden de las polizas y
 * de sus transacciones lo determina el llamador.</p>
 */
public final class GeneradorPolizasXml {

    private static final String NS_POLIZAS =
            "www.sat.gob.mx/esquemas/ContabilidadE/1_3/PolizasPeriodo";
    private static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String SCHEMA_LOCATION =
            "www.sat.gob.mx/esquemas/ContabilidadE/1_3/PolizasPeriodo "
                    + "http://www.sat.gob.mx/esquemas/ContabilidadE/1_3/PolizasPeriodo/PolizasPeriodo_1_3.xsd";
    private static final String PREFIJO = "PLZ";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    private GeneradorPolizasXml() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Genera el Polizas_XML del SAT.
     *
     * @param encabezado    datos de encabezado (version, RFC, anio, mes).
     * @param tipoSolicitud tipo de solicitud: {@code AF} (Acto de Fiscalizacion),
     *                      {@code FC} (Fiscalizacion Compulsa), {@code DE}
     *                      (Devolucion) o {@code CO} (Compensacion).
     * @param polizas       polizas del periodo (ya ordenadas de forma determinista).
     * @return el XML de las polizas del periodo (UTF-8).
     * @throws IllegalStateException si ocurre un error de serializacion XML.
     */
    public static String generar(EncabezadoSat encabezado, String tipoSolicitud,
                                 List<PolizaSat> polizas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            XMLStreamWriter w = XMLOutputFactory.newFactory()
                    .createXMLStreamWriter(salida, StandardCharsets.UTF_8.name());
            w.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");

            w.setPrefix(PREFIJO, NS_POLIZAS);
            w.writeStartElement(NS_POLIZAS, "Polizas");
            w.writeNamespace(PREFIJO, NS_POLIZAS);
            w.writeNamespace("xsi", NS_XSI);
            w.writeAttribute(NS_XSI, "schemaLocation", SCHEMA_LOCATION);
            w.writeAttribute("Version", encabezado.version());
            w.writeAttribute("RFC", encabezado.rfc());
            w.writeAttribute("Mes", encabezado.mes());
            w.writeAttribute("Anio", Integer.toString(encabezado.anio()));
            w.writeAttribute("TipoSolicitud", tipoSolicitud);

            for (PolizaSat poliza : polizas) {
                w.writeStartElement(NS_POLIZAS, "Poliza");
                w.writeAttribute("NumUnIdenPol", nvl(poliza.numUnIdenPol()));
                w.writeAttribute("Fecha", poliza.fecha() == null ? "" : poliza.fecha().format(FECHA));
                w.writeAttribute("Concepto", nvl(poliza.concepto()));

                List<TransaccionSat> transacciones =
                        poliza.transacciones() == null ? List.of() : poliza.transacciones();
                for (TransaccionSat t : transacciones) {
                    w.writeStartElement(NS_POLIZAS, "Transaccion");
                    w.writeAttribute("NumCta", nvl(t.numCta()));
                    w.writeAttribute("DesCta", nvl(t.desCta()));
                    w.writeAttribute("Concepto", nvl(t.concepto()));
                    w.writeAttribute("Debe", XmlUtil.importe(t.debe()));
                    w.writeAttribute("Haber", XmlUtil.importe(t.haber()));
                    w.writeEndElement();
                }

                w.writeEndElement();
            }

            w.writeEndElement();
            w.writeEndDocument();
            w.flush();
            w.close();
        } catch (XMLStreamException ex) {
            throw new IllegalStateException("No se pudo generar el Polizas_XML del SAT.", ex);
        }
        return salida.toString(StandardCharsets.UTF_8);
    }

    private static String nvl(String v) {
        return (v == null) ? "" : v;
    }
}
