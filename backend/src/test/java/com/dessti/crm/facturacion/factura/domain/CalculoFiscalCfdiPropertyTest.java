package com.dessti.crm.facturacion.factura.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.facturacion.factura.domain.CalculoFiscalCfdi.ImportesFiscales;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 4: Calculo fiscal
 * de la Factura (CFDI)</strong> (Req 34.2).
 *
 * <p>Reutiliza la pieza de produccion pura
 * {@link CalculoFiscalCfdi#calcular(BigDecimal, BigDecimal)} sin base de datos ni
 * contexto de Spring. El subtotal se genera a partir de un numero entero de
 * centavos para cubrir uniformemente el espacio monetario con escala 2 exacta, con
 * y sin retenciones.</p>
 *
 * <h2>Invariantes verificados (Property 4)</h2>
 * <ul>
 *   <li>{@code iva == round(subtotal * 0.16, 2, HALF_UP)}, escala 2.</li>
 *   <li>{@code retenciones == round(subtotal * tasaRetencion, 2, HALF_UP)}; con
 *       tasa 0 las retenciones son exactamente {@code 0.00}.</li>
 *   <li>{@code total == round(subtotal + iva - retenciones, 2, HALF_UP)}, escala 2.</li>
 *   <li>El calculo es puro y determinista: mismas entradas -> mismo resultado.</li>
 * </ul>
 */
class CalculoFiscalCfdiPropertyTest {

    private static final int ESCALA = CalculoFiscalCfdi.ESCALA_MONETARIA;   // 2
    private static final BigDecimal TASA_IVA = CalculoFiscalCfdi.TASA_IVA;   // 0.16

    /** Subtotal maximo expresado en centavos (subtotal <= 999,999,999.99). */
    private static final long MAX_CENTAVOS = 99_999_999_999L;

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Subtotal con escala 2 exacta en {@code [0.00, 999,999,999.99]}. */
    @Provide
    Arbitrary<BigDecimal> subtotales() {
        return Arbitraries.longs()
                .between(0L, MAX_CENTAVOS)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
    }

    /**
     * Tasa de retencion en {@code [0, 1]} con escala 4 (por ejemplo 0.1067 para el
     * 10.67% de ISR de servicios profesionales), generada a partir de puntos base
     * (0..10000).
     */
    @Provide
    Arbitrary<BigDecimal> tasasRetencion() {
        return Arbitraries.integers()
                .between(0, 10_000)
                .map(puntos -> new BigDecimal(puntos).movePointLeft(4));
    }

    @Provide
    Arbitrary<CasoFiscal> casosFiscales() {
        return Combinators.combine(subtotales(), tasasRetencion()).as(CasoFiscal::new);
    }

    // ----------------------------------------------------------------------
    // Property 4 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 4: Cálculo fiscal de la Factura (CFDI)
    @Property(tries = 1000)
    void ivaEsSubtotalPorDieciseisPorcientoRedondeadoHalfUp(
            @ForAll("subtotales") BigDecimal subtotal) {
        ImportesFiscales importes = CalculoFiscalCfdi.calcular(subtotal, CalculoFiscalCfdi.SIN_RETENCION);

        BigDecimal esperado = subtotal.setScale(ESCALA, RoundingMode.HALF_UP)
                .multiply(TASA_IVA)
                .setScale(ESCALA, RoundingMode.HALF_UP);

        assertThat(importes.iva())
                .as("iva == round(subtotal * 0.16, 2, HALF_UP)")
                .isEqualByComparingTo(esperado);
        assertThat(importes.iva().scale()).as("el IVA tiene escala 2").isEqualTo(ESCALA);
    }

    // Feature: crm-anuncios-luminosos, Property 4: Cálculo fiscal de la Factura (CFDI)
    @Property(tries = 1000)
    void retencionesSonSubtotalPorTasaRedondeadoHalfUp(@ForAll("casosFiscales") CasoFiscal caso) {
        ImportesFiscales importes = CalculoFiscalCfdi.calcular(caso.subtotal, caso.tasaRetencion);

        BigDecimal esperado = caso.subtotal.setScale(ESCALA, RoundingMode.HALF_UP)
                .multiply(caso.tasaRetencion)
                .setScale(ESCALA, RoundingMode.HALF_UP);

        assertThat(importes.retenciones())
                .as("retenciones == round(subtotal * tasaRetencion, 2, HALF_UP)")
                .isEqualByComparingTo(esperado);
        assertThat(importes.retenciones().scale())
                .as("las retenciones tienen escala 2")
                .isEqualTo(ESCALA);
    }

    // Feature: crm-anuncios-luminosos, Property 4: Cálculo fiscal de la Factura (CFDI)
    @Property(tries = 1000)
    void sinRetencionLasRetencionesSonCero(@ForAll("subtotales") BigDecimal subtotal) {
        ImportesFiscales importes = CalculoFiscalCfdi.calcular(subtotal, CalculoFiscalCfdi.SIN_RETENCION);

        assertThat(importes.retenciones())
                .as("sin retencion, retenciones == 0.00")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Feature: crm-anuncios-luminosos, Property 4: Cálculo fiscal de la Factura (CFDI)
    @Property(tries = 1000)
    void totalEsSubtotalMasIvaMenosRetencionesRedondeadoHalfUp(
            @ForAll("casosFiscales") CasoFiscal caso) {
        ImportesFiscales importes = CalculoFiscalCfdi.calcular(caso.subtotal, caso.tasaRetencion);

        BigDecimal esperado = importes.subtotal()
                .add(importes.iva())
                .subtract(importes.retenciones())
                .setScale(ESCALA, RoundingMode.HALF_UP);

        assertThat(importes.total())
                .as("total == round(subtotal + iva - retenciones, 2, HALF_UP)")
                .isEqualByComparingTo(esperado);
        assertThat(importes.total().scale()).as("el total tiene escala 2").isEqualTo(ESCALA);
    }

    // Feature: crm-anuncios-luminosos, Property 4: Cálculo fiscal de la Factura (CFDI)
    @Property(tries = 1000)
    void calculoFiscalEsPuroYDeterminista(@ForAll("casosFiscales") CasoFiscal caso) {
        ImportesFiscales primero = CalculoFiscalCfdi.calcular(caso.subtotal, caso.tasaRetencion);
        ImportesFiscales segundo = CalculoFiscalCfdi.calcular(caso.subtotal, caso.tasaRetencion);

        assertThat(segundo.iva()).isEqualByComparingTo(primero.iva());
        assertThat(segundo.retenciones()).isEqualByComparingTo(primero.retenciones());
        assertThat(segundo.total())
                .as("mismas entradas -> mismo total (calculo puro y determinista)")
                .isEqualByComparingTo(primero.total());
    }

    /** Par (subtotal, tasa de retencion) generado para ejercitar el calculo fiscal. */
    private static final class CasoFiscal {
        private final BigDecimal subtotal;
        private final BigDecimal tasaRetencion;

        private CasoFiscal(BigDecimal subtotal, BigDecimal tasaRetencion) {
            this.subtotal = subtotal;
            this.tasaRetencion = tasaRetencion;
        }
    }
}
