package com.dessti.crm.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.facturacion.factura.domain.DatosFiscalesReceptor;
import com.dessti.crm.facturacion.factura.domain.EstadoFactura;
import com.dessti.crm.facturacion.factura.domain.Factura;
import com.dessti.crm.facturacion.notacredito.domain.EstadoNotaCredito;
import com.dessti.crm.facturacion.notacredito.domain.NotaCredito;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 21: Inmutabilidad
 * de CFDI y recibos timbrados</strong> (Req 35.3, 35.6, 37.3).
 *
 * <p>Ejercita directamente las entidades de dominio {@link Factura} y
 * {@link NotaCredito} (sin base de datos ni Spring). Para cualquier CFDI ya
 * timbrado (Factura en {@code timbrada}/{@code cancelada} o Nota de Credito en
 * {@code timbrada}/{@code cancelada}), cualquier intento de mutar sus datos
 * fiscales o de re-timbrar se rechaza; solo las transiciones de cancelacion
 * definidas por la maquina de estados tienen exito.</p>
 *
 * <p>Nota de alcance: el Recibo_Nomina timbrado (Req 41.7) pertenece al bloque 35;
 * aqui se cubre la inmutabilidad del CFDI de la Factura y de la Nota de Credito.</p>
 *
 * <h2>Invariantes verificados (Property 21)</h2>
 * <ul>
 *   <li>Una Factura {@code timbrada} rechaza {@code modificarDatosFiscales} con 422
 *       y conserva sus datos fiscales y su Folio_Fiscal (Req 35.3).</li>
 *   <li>Una Factura {@code timbrada} no admite re-timbrado ni la transicion
 *       {@code borrador} (409); solo admite iniciar la cancelacion.</li>
 *   <li>Tras cancelar (borrador->timbrada->cancelacion_en_proceso->cancelada), el
 *       Folio_Fiscal y el sello se conservan como historico (Req 35.6) y los datos
 *       fiscales siguen inmutables.</li>
 *   <li>Una Nota de Credito {@code timbrada} no admite re-timbrado (409) y conserva
 *       su Folio_Fiscal aun tras la cancelacion (Req 37.3).</li>
 * </ul>
 */
class InmutabilidadCfdiTimbradoPropertyTest {

    private static final String ACTOR = "tester";
    private static final long MAX_CENTAVOS = 99_999_999_999L;

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Datos fiscales validos del receptor (RFC de persona moral). */
    @Provide
    Arbitrary<DatosFiscalesReceptor> datosFiscales() {
        Arbitrary<String> rfc = Arbitraries.of(
                "AAA010101AAA", "BBB020202BB2", "XYZ990101QW9", "ABC800101XYZ");
        Arbitrary<String> nombre = Arbitraries.of(
                "Cliente Uno SA de CV", "Comercial Dos", "Receptor Tres SA");
        Arbitrary<String> cp = Arbitraries.of("01000", "44100", "64000", "06600");
        Arbitrary<String> regimen = Arbitraries.of("601", "603", "612", "626");
        Arbitrary<String> uso = Arbitraries.of("G01", "G03", "I01", "P01");
        return Combinators.combine(rfc, nombre, cp, regimen, uso)
                .as(DatosFiscalesReceptor::validar);
    }

    /** Subtotal con escala 2 en {@code [0.01, 999,999,999.99]}. */
    @Provide
    Arbitrary<BigDecimal> subtotales() {
        return Arbitraries.longs()
                .between(1L, MAX_CENTAVOS)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(2));
    }

    private static Factura facturaTimbrada(DatosFiscalesReceptor datos, BigDecimal subtotal) {
        Factura factura = Factura.emitirDesdeCotizacion(
                UUID.randomUUID(), UUID.randomUUID(), datos, subtotal, BigDecimal.ZERO, ACTOR);
        factura.timbrar(UUID.randomUUID(), "SELLO-TEST", Instant.now(), ACTOR);
        return factura;
    }

    // ----------------------------------------------------------------------
    // Property 21 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 21: Inmutabilidad de CFDI y recibos timbrados
    @Property(tries = 1000)
    void facturaTimbradaRechazaModificarDatosFiscales(
            @ForAll("datosFiscales") DatosFiscalesReceptor datos,
            @ForAll("subtotales") BigDecimal subtotal,
            @ForAll("datosFiscales") DatosFiscalesReceptor otros) {
        Factura factura = facturaTimbrada(datos, subtotal);
        UUID folioAntes = factura.getFolioFiscal();
        String rfcAntes = factura.getReceptorRfc();

        assertThatThrownBy(() -> factura.modificarDatosFiscales(otros, subtotal, BigDecimal.ZERO, ACTOR))
                .as("una Factura timbrada no admite modificar datos fiscales (Req 35.3)")
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(factura.getEstado()).isEqualTo(EstadoFactura.TIMBRADA);
        assertThat(factura.getFolioFiscal())
                .as("el Folio_Fiscal se conserva inmutable")
                .isEqualTo(folioAntes);
        assertThat(factura.getReceptorRfc())
                .as("los datos fiscales se conservan inmutables")
                .isEqualTo(rfcAntes);
        assertThat(factura.datosFiscalesModificables()).isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 21: Inmutabilidad de CFDI y recibos timbrados
    @Property(tries = 1000)
    void facturaTimbradaNoAdmiteRetimbradoNiVolverABorrador(
            @ForAll("datosFiscales") DatosFiscalesReceptor datos,
            @ForAll("subtotales") BigDecimal subtotal) {
        Factura factura = facturaTimbrada(datos, subtotal);

        // Re-timbrar (borrador->timbrada) sobre una ya timbrada es transicion invalida.
        assertThatThrownBy(() -> factura.timbrar(UUID.randomUUID(), "SELLO-2", Instant.now(), ACTOR))
                .as("una Factura timbrada no puede re-timbrarse (409)")
                .isInstanceOf(TransicionInvalidaException.class);

        // Solo la transicion de cancelacion definida tiene exito.
        assertThatCode(() -> factura.iniciarCancelacion("02", ACTOR))
                .as("timbrada -> cancelacion_en_proceso es valida")
                .doesNotThrowAnyException();
        assertThat(factura.getEstado()).isEqualTo(EstadoFactura.CANCELACION_EN_PROCESO);
    }

    // Feature: crm-anuncios-luminosos, Property 21: Inmutabilidad de CFDI y recibos timbrados
    @Property(tries = 1000)
    void facturaCanceladaConservaFolioYSiguenInmutablesLosDatos(
            @ForAll("datosFiscales") DatosFiscalesReceptor datos,
            @ForAll("subtotales") BigDecimal subtotal,
            @ForAll("datosFiscales") DatosFiscalesReceptor otros) {
        Factura factura = facturaTimbrada(datos, subtotal);
        UUID folio = factura.getFolioFiscal();
        String sello = factura.getSelloSat();

        factura.iniciarCancelacion("01", ACTOR);
        factura.confirmarCancelacion(ACTOR);

        assertThat(factura.getEstado()).isEqualTo(EstadoFactura.CANCELADA);
        assertThat(factura.getFolioFiscal())
                .as("el Folio_Fiscal se conserva como historico tras la cancelacion (Req 35.6)")
                .isEqualTo(folio);
        assertThat(factura.getSelloSat()).isEqualTo(sello);
        assertThatThrownBy(() -> factura.modificarDatosFiscales(otros, subtotal, BigDecimal.ZERO, ACTOR))
                .as("una Factura cancelada sigue rechazando la modificacion de datos fiscales")
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 21: Inmutabilidad de CFDI y recibos timbrados
    @Property(tries = 1000)
    void notaCreditoTimbradaNoAdmiteRetimbradoYConservaFolioTrasCancelar(
            @ForAll("subtotales") BigDecimal montoBase) {
        BigDecimal monto = montoBase.min(new BigDecimal("100000.00")).max(new BigDecimal("0.01"));
        NotaCredito nota = NotaCredito.emitir(UUID.randomUUID(), UUID.randomUUID(), monto, ACTOR);
        nota.timbrar(UUID.randomUUID(), "SELLO-NC", Instant.now(), ACTOR);
        UUID folio = nota.getFolioFiscal();

        assertThatThrownBy(() -> nota.timbrar(UUID.randomUUID(), "SELLO-NC2", Instant.now(), ACTOR))
                .as("una Nota de Credito timbrada no puede re-timbrarse (409)")
                .isInstanceOf(TransicionInvalidaException.class);

        nota.cancelar(ACTOR);
        assertThat(nota.getEstado()).isEqualTo(EstadoNotaCredito.CANCELADA);
        assertThat(nota.getFolioFiscal())
                .as("la Nota de Credito conserva su Folio_Fiscal como historico (Req 37.3)")
                .isEqualTo(folio);
    }
}
