package com.dessti.crm.platform.monetizacion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.monetizacion.domain.FacturaRenta;
import com.dessti.crm.platform.monetizacion.domain.FacturaRenta.LineaCruda;

/**
 * Pruebas del {@link FacturaRentaPdfService}: genera un PDF no vacio con la
 * firma {@code %PDF} y NO lanza excepcion cuando la Empresa receptora tiene
 * campos opcionales vacios o es {@code null} (degradacion controlada).
 */
class FacturaRentaPdfServiceTest {

    private static final UUID TENANT = UUID.fromString("aabbccdd-1111-2222-3333-444455556666");

    private final FacturaRentaPdfService servicio = new FacturaRentaPdfService();

    private FacturaRenta facturaConDosLineas() {
        List<LineaCruda> lineas = List.of(
                new LineaCruda("comercial", "Comercial (CRM)", new BigDecimal("500.00")),
                new LineaCruda("facturacion", "Facturacion CFDI", new BigDecimal("300.50")));
        return FacturaRenta.emitir(TENANT, LocalDate.of(2025, 6, 1), "MXN", lineas, "super_admin");
    }

    private Empresa empresaCompleta() {
        Empresa e = Empresa.crear("Signos Brillantes S.A. de C.V.", "SBR900101AAA",
                UUID.randomUUID(), "super_admin");
        e.asignarDatosDescriptivos(new com.dessti.crm.platform.empresas.DatosDescriptivosEmpresa(
                "Bright Signs", "contacto@brightsigns.mx", "55-1234-5678", "https://brightsigns.mx",
                "Av. Reforma 100", "Ciudad de Mexico", "CDMX", "06600", "Mexico", null, null),
                "super_admin");
        return e;
    }

    private static void assertEsPdf(byte[] pdf) {
        assertThat(pdf).isNotEmpty();
        // Firma de archivo PDF: los primeros bytes son "%PDF".
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("genera un PDF no vacio con la firma %PDF con Empresa y emisor completos")
    void generaPdfConDatosCompletos() {
        EmisorProperties emisor = new EmisorProperties("Dess-TI", "DTI010101AAA",
                "Calle Falsa 123, Monterrey, NL", "hola@dessti.com", "https://dessti.com");

        byte[] pdf = servicio.generar(facturaConDosLineas(), empresaCompleta(), emisor);

        assertEsPdf(pdf);
    }

    @Test
    @DisplayName("no lanza excepcion con campos opcionales de la Empresa vacios (se omiten)")
    void noFallaConCamposOpcionalesVacios() {
        // Empresa minima: solo nombre y RFC; el resto de campos quedan nulos.
        Empresa minima = Empresa.crear("Empresa Minima SA", "EMI900101AAA",
                UUID.randomUUID(), "super_admin");
        // Emisor con solo el nombre (resto vacio, se omite).
        EmisorProperties emisor = new EmisorProperties(null, null, null, null, null);

        assertThatCode(() -> {
            byte[] pdf = servicio.generar(facturaConDosLineas(), minima, emisor);
            assertEsPdf(pdf);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("genera el PDF en modo degradado cuando la Empresa receptora es null")
    void generaPdfSinEmpresa() {
        EmisorProperties emisor = new EmisorProperties("Dess-TI", "", "", "", "");

        byte[] pdf = servicio.generar(facturaConDosLineas(), null, emisor);

        assertEsPdf(pdf);
    }

    @Test
    @DisplayName("formatea el monto en es-MX prefijado con el codigo de moneda")
    void formateaMonto() {
        assertThat(servicio.formatearMonto(new BigDecimal("1234.5"), "MXN"))
                .isEqualTo("MXN 1,234.50");
        // Codigo no ISO: se conserva como prefijo textual sin fallar.
        assertThat(servicio.formatearMonto(new BigDecimal("10"), "XYZ"))
                .isEqualTo("XYZ 10.00");
    }

    @Test
    @DisplayName("formatea el periodo como 'Mes yyyy' capitalizado (es-MX)")
    void formateaPeriodo() {
        assertThat(servicio.formatearPeriodo(LocalDate.of(2025, 6, 1)))
                .isEqualTo("Junio 2025");
    }
}
