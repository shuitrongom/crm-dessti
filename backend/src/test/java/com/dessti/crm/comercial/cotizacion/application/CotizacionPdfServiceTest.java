package com.dessti.crm.comercial.cotizacion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.comercial.cotizacion.application.DatosClientePort.DatosCliente;
import com.dessti.crm.comercial.cotizacion.domain.Cotizacion;
import com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion;

/**
 * Pruebas del {@link CotizacionPdfService}: genera un PDF no vacio con la firma
 * {@code %PDF}, muestra los datos de la Empresa (emisor) y NO filtra los datos de
 * plataforma (Dess-TI), degradando sin excepcion cuando faltan campos (V60,
 * Req 1).
 */
class CotizacionPdfServiceTest {

    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** RFC de plataforma (Dess-TI) que NUNCA debe aparecer en la cotizacion (Req 1.6). */
    private static final String RFC_PLATAFORMA = "DTI200101AB1";

    private final CotizacionPdfService servicio = new CotizacionPdfService();

    private Cotizacion cotizacionCompleta() {
        Cotizacion cotizacion = Cotizacion.crear(CLIENTE,
                List.of(
                        PartidaCotizacion.crear(null, "Anuncio luminoso 3x2", 2, new BigDecimal("1500.00"), "ventas"),
                        PartidaCotizacion.crear(null, "Instalacion", 1, new BigDecimal("800.00"), "ventas")),
                "ventas");
        cotizacion.aplicarDatosDescriptivos(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15),
                "Pago 50% anticipo, 50% contra entrega.", "Precios sujetos a disponibilidad.", "MXN", "ventas");
        cotizacion.asignarFolio("COT-2026-0001");
        return cotizacion;
    }

    private static void assertEsPdf(byte[] pdf) {
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    /** Extrae el texto visible del PDF (el contenido va comprimido con FlateDecode). */
    private static String textoPdf(byte[] pdf) {
        try {
            com.lowagie.text.pdf.PdfReader lector = new com.lowagie.text.pdf.PdfReader(pdf);
            try {
                com.lowagie.text.pdf.parser.PdfTextExtractor extractor =
                        new com.lowagie.text.pdf.parser.PdfTextExtractor(lector);
                StringBuilder texto = new StringBuilder();
                for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                    texto.append(extractor.getTextFromPage(pagina)).append('\n');
                }
                return texto.toString();
            } finally {
                lector.close();
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("No se pudo leer el PDF de prueba.", ex);
        }
    }

    @Test
    @DisplayName("genera un PDF con la razon social de la Empresa y NO filtra Dess-TI ni su RFC (Req 1.1, 1.6)")
    void generaPdfConEmisorEmpresaSinPlataforma() {
        DatosEmisor emisor = new DatosEmisor("Anuncios del Norte S.A. de C.V.", "Anuncios Norte",
                "ANO120101AB1", "Av. Constitucion 100, Monterrey, Nuevo Leon", "contacto@an.mx",
                "https://an.mx");
        DatosCliente cliente = new DatosCliente(CLIENTE, "Anuncios ACME", "AAA010101AAA", "ventas@acme.mx");

        byte[] pdf = servicio.generar(cotizacionCompleta(), cliente, emisor);

        assertEsPdf(pdf);
        String texto = textoPdf(pdf);
        assertThat(texto).contains("Anuncios del Norte S.A. de C.V.");
        assertThat(texto).contains("Anuncios Norte"); // nombre comercial presente
        assertThat(texto).doesNotContain("Dess-TI");
        assertThat(texto).doesNotContain(RFC_PLATAFORMA);
    }

    @Test
    @DisplayName("con RFC y direccion vacios se omiten esas lineas sin excepcion (Req 1.5, 3.1)")
    void generaPdfConEmisorParcialOmiteLineasVacias() {
        // Solo razon social y correo; RFC/direccion/nombre comercial vacios.
        DatosEmisor emisor = new DatosEmisor("Rotulos del Bajio", null, null, null,
                "hola@rotulos.mx", null);
        DatosCliente cliente = new DatosCliente(CLIENTE, "Cliente Uno", null, null);

        assertThatCode(() -> {
            byte[] pdf = servicio.generar(cotizacionCompleta(), cliente, emisor);
            assertEsPdf(pdf);
            String texto = textoPdf(pdf);
            assertThat(texto).contains("Rotulos del Bajio");
            assertThat(texto).doesNotContain("Dess-TI");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("con emisor minimo (solo nombre) y Cliente nulo degrada sin excepcion (Req 3.1)")
    void generaPdfConEmisorMinimoSinCliente() {
        DatosEmisor emisor = DatosEmisor.minimo("Empresa");

        assertThatCode(() -> {
            byte[] pdf = servicio.generar(cotizacionCompleta(), null, emisor);
            assertEsPdf(pdf);
            assertThat(textoPdf(pdf)).doesNotContain("Dess-TI");
        }).doesNotThrowAnyException();
    }
}
