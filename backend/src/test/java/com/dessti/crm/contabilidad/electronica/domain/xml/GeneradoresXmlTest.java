package com.dessti.crm.contabilidad.electronica.domain.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

import com.dessti.crm.contabilidad.electronica.domain.modelo.CuentaCatalogoSat;
import com.dessti.crm.contabilidad.electronica.domain.modelo.PolizaSat;
import com.dessti.crm.contabilidad.electronica.domain.modelo.RenglonBalanzaSat;
import com.dessti.crm.contabilidad.electronica.domain.modelo.TransaccionSat;

/**
 * Pruebas de dominio PURO de los generadores XML de Contabilidad Electronica SAT
 * (Anexo 24), Req 2, 3, 4, 7.6. Sin Spring ni base de datos: verifican que el XML
 * generado es bien formado (parseable), contiene el namespace, los atributos del
 * encabezado y los elementos esperados, con importes a dos decimales y escape de
 * caracteres especiales.
 */
class GeneradoresXmlTest {

    private static final EncabezadoSat ENCABEZADO =
            EncabezadoSat.de("1.3", "XAXX010101000", 2026, 9);

    private static void assertBienFormado(String xml) {
        assertThatCode(() -> {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        }).as("el XML generado debe ser bien formado (parseable)").doesNotThrowAnyException();
    }

    @Test
    void catalogoGeneraXmlBienFormadoConNamespaceYCuentas() {
        String xml = GeneradorCatalogoXml.generar(ENCABEZADO, List.of(
                new CuentaCatalogoSat("101.01", "101-01", "Caja y efectivo", 2, "D"),
                new CuentaCatalogoSat("201.01", "201-01", "Proveedores nacionales", 2, "A")));

        assertBienFormado(xml);
        assertThat(xml)
                .contains("www.sat.gob.mx/esquemas/ContabilidadE/1_3/CatalogoCuentas")
                .contains("Version=\"1.3\"")
                .contains("RFC=\"XAXX010101000\"")
                .contains("Mes=\"09\"")
                .contains("Anio=\"2026\"")
                .contains("CodAgrup=\"101.01\"")
                .contains("NumCta=\"101-01\"")
                .contains("Desc=\"Caja y efectivo\"")
                .contains("Natur=\"D\"")
                .contains("CodAgrup=\"201.01\"");
    }

    @Test
    void balanzaGeneraXmlConImportesADosDecimales() {
        String xml = GeneradorBalanzaXml.generar(ENCABEZADO, "N", List.of(
                new RenglonBalanzaSat("101-01",
                        new BigDecimal("100.5"), new BigDecimal("50"),
                        new BigDecimal("20"), new BigDecimal("130.5"))));

        assertBienFormado(xml);
        assertThat(xml)
                .contains("www.sat.gob.mx/esquemas/ContabilidadE/1_3/BalanzaComprobacion")
                .contains("TipoEnvio=\"N\"")
                .contains("NumCta=\"101-01\"")
                .contains("SaldoIni=\"100.50\"")
                .contains("Debe=\"50.00\"")
                .contains("Haber=\"20.00\"")
                .contains("SaldoFin=\"130.50\"");
    }

    @Test
    void polizasGeneraXmlConTransaccionesYFecha() {
        String xml = GeneradorPolizasXml.generar(ENCABEZADO, "AF", List.of(
                new PolizaSat("P1", LocalDate.of(2026, 9, 15), "Venta del dia", List.of(
                        new TransaccionSat("105-01", "Clientes", "Venta",
                                new BigDecimal("116"), BigDecimal.ZERO),
                        new TransaccionSat("401-01", "Ingresos", "Venta",
                                BigDecimal.ZERO, new BigDecimal("100"))))));

        assertBienFormado(xml);
        assertThat(xml)
                .contains("www.sat.gob.mx/esquemas/ContabilidadE/1_3/PolizasPeriodo")
                .contains("TipoSolicitud=\"AF\"")
                .contains("NumUnIdenPol=\"P1\"")
                .contains("Fecha=\"2026-09-15\"")
                .contains("NumCta=\"105-01\"")
                .contains("Debe=\"116.00\"")
                .contains("Haber=\"100.00\"");
    }

    @Test
    void escapaCaracteresEspecialesEnAtributos() {
        String xml = GeneradorCatalogoXml.generar(ENCABEZADO, List.of(
                new CuentaCatalogoSat("100", "100", "Ventas & servicios <\"especiales\">", 1, "A")));

        // Debe seguir siendo parseable pese a los caracteres especiales.
        assertBienFormado(xml);
        // El & literal no debe aparecer sin escapar (StAX lo convierte a &amp;).
        assertThat(xml).contains("&amp;");
    }

    @Test
    void importesNulosSeTratanComoCero() {
        String xml = GeneradorBalanzaXml.generar(ENCABEZADO, "N", List.of(
                new RenglonBalanzaSat("999", null, null, null, null)));
        assertBienFormado(xml);
        assertThat(xml).contains("SaldoIni=\"0.00\"").contains("Debe=\"0.00\"");
    }
}
