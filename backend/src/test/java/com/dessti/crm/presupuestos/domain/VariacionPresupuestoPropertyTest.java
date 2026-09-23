package com.dessti.crm.presupuestos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 36: Variacion de
 * presupuesto en importe y porcentaje</strong> (Req 62.6, 62.7).
 *
 * <p>Ejercita el nucleo de calculo PURO {@link CalculoVariacionPresupuesto} contra
 * un modelo de referencia calculado de forma independiente en el propio test, sin
 * base de datos ni contexto de Spring. Los montos se generan a partir de centavos
 * enteros (escala 2 exacta) para que las comparaciones por {@code compareTo} sean
 * deterministas y sin ambiguedad de redondeo.</p>
 *
 * <h2>Invariantes verificados (Property 36)</h2>
 * <ol>
 *   <li><strong>Variacion en importe (Req 62.6, 62.7):</strong>
 *       {@code variacionImporte == round(real - presupuestado, 2, HALF_UP)} para
 *       cualesquiera montos.</li>
 *   <li><strong>Variacion en porcentaje (Req 62.7):</strong> cuando
 *       {@code presupuestado > 0},
 *       {@code variacionPorcentaje == round(variacionImporte / presupuestado * 100, 2)};
 *       con {@code presupuestado == 0} el porcentaje es 0 (regla documentada).</li>
 *   <li><strong>Favorable / desfavorable (Req 62.6):</strong> para
 *       {@link TipoPresupuesto#INGRESO}, favorable &hArr; {@code real >= presupuestado};
 *       para {@link TipoPresupuesto#EGRESO}, favorable &hArr;
 *       {@code real <= presupuestado}.</li>
 *   <li><strong>Supera umbral (Req 62.3):</strong> {@code superaUmbral} &hArr;
 *       {@code abs(variacionPorcentaje) > umbral * 100}.</li>
 * </ol>
 */
class VariacionPresupuestoPropertyTest {

    private static final int ESCALA = 2;
    private static final BigDecimal CIEN = new BigDecimal("100");

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Monto >= 0 a escala 2, en centavos de 0 a 100_000_000_00 (0 .. 100,000,000.00). */
    private static Arbitrary<BigDecimal> montoNoNegativo() {
        return Arbitraries.longs()
                .between(0L, 10_000_000_000L)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
    }

    /** Monto presupuestado estrictamente positivo (para la rama con porcentaje definido). */
    private static Arbitrary<BigDecimal> montoPositivo() {
        return Arbitraries.longs()
                .between(1L, 10_000_000_000L)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
    }

    /** Umbral como fraccion no negativa a escala 4 (0.0000 .. 1.0000). */
    private static Arbitrary<BigDecimal> umbralFraccion() {
        return Arbitraries.longs()
                .between(0L, 10_000L)
                .map(diezmil -> new BigDecimal(diezmil).movePointLeft(4));
    }

    @Provide
    Arbitrary<BigDecimal> presupuestados() {
        return montoNoNegativo();
    }

    @Provide
    Arbitrary<BigDecimal> presupuestadosPositivos() {
        return montoPositivo();
    }

    @Provide
    Arbitrary<BigDecimal> reales() {
        return montoNoNegativo();
    }

    @Provide
    Arbitrary<BigDecimal> umbrales() {
        return umbralFraccion();
    }

    @Provide
    Arbitrary<TipoPresupuesto> tipos() {
        return Arbitraries.of(TipoPresupuesto.class);
    }

    // ----------------------------------------------------------------------
    // Property 36 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 36: Variación de presupuesto en importe y porcentaje
    @Property(tries = 1000)
    void variacionImporteEsRealMenosPresupuestadoRedondeado(
            @ForAll("presupuestados") BigDecimal presupuestado,
            @ForAll("reales") BigDecimal real,
            @ForAll("tipos") TipoPresupuesto tipo,
            @ForAll("umbrales") BigDecimal umbral) {

        ResultadoVariacion r = CalculoVariacionPresupuesto.calcular(presupuestado, real, tipo, umbral);

        BigDecimal esperado = real.subtract(presupuestado).setScale(ESCALA, RoundingMode.HALF_UP);
        assertThat(r.variacionImporte())
                .as("variacion en importe == round(real(%s) - presupuestado(%s), 2)", real, presupuestado)
                .isEqualByComparingTo(esperado);
    }

    // Feature: crm-anuncios-luminosos, Property 36: Variación de presupuesto en importe y porcentaje
    @Property(tries = 1000)
    void variacionPorcentajeEsImporteEntrePresupuestadoPorCien(
            @ForAll("presupuestadosPositivos") BigDecimal presupuestado,
            @ForAll("reales") BigDecimal real,
            @ForAll("tipos") TipoPresupuesto tipo,
            @ForAll("umbrales") BigDecimal umbral) {

        ResultadoVariacion r = CalculoVariacionPresupuesto.calcular(presupuestado, real, tipo, umbral);

        BigDecimal importe = real.subtract(presupuestado).setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal esperado = importe.multiply(CIEN)
                .divide(presupuestado.setScale(ESCALA, RoundingMode.HALF_UP), ESCALA, RoundingMode.HALF_UP);
        assertThat(r.variacionPorcentaje())
                .as("porcentaje == round(importe/presupuestado*100, 2) con presupuestado > 0")
                .isEqualByComparingTo(esperado);
    }

    // Feature: crm-anuncios-luminosos, Property 36: Variación de presupuesto en importe y porcentaje
    @Property(tries = 1000)
    void presupuestoCeroProducePorcentajeCeroYNoSuperaUmbral(
            @ForAll("reales") BigDecimal real,
            @ForAll("tipos") TipoPresupuesto tipo,
            @ForAll("umbrales") BigDecimal umbral) {

        ResultadoVariacion r = CalculoVariacionPresupuesto.calcular(
                BigDecimal.ZERO, real, tipo, umbral);

        assertThat(r.variacionPorcentaje())
                .as("con presupuestado 0, el porcentaje es 0 (regla documentada, evita division por cero)")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.superaUmbral())
                .as("con porcentaje 0 nunca se supera un umbral no negativo")
                .isFalse();
        // El importe se sigue calculando con normalidad.
        assertThat(r.variacionImporte())
                .as("con presupuestado 0, la variacion en importe es el real")
                .isEqualByComparingTo(real.setScale(ESCALA, RoundingMode.HALF_UP));
    }

    // Feature: crm-anuncios-luminosos, Property 36: Variación de presupuesto en importe y porcentaje
    @Property(tries = 1000)
    void favorableSigueLaConvencionDeSignoDelTipo(
            @ForAll("presupuestados") BigDecimal presupuestado,
            @ForAll("reales") BigDecimal real,
            @ForAll("tipos") TipoPresupuesto tipo,
            @ForAll("umbrales") BigDecimal umbral) {

        ResultadoVariacion r = CalculoVariacionPresupuesto.calcular(presupuestado, real, tipo, umbral);

        int comparacion = real.compareTo(presupuestado);
        boolean favorableEsperado = switch (tipo) {
            case INGRESO -> comparacion >= 0;
            case EGRESO -> comparacion <= 0;
        };
        assertThat(r.favorable())
                .as("favorable segun tipo %s: real(%s) vs presupuestado(%s)", tipo, real, presupuestado)
                .isEqualTo(favorableEsperado);
    }

    // Feature: crm-anuncios-luminosos, Property 36: Variación de presupuesto en importe y porcentaje
    @Property(tries = 1000)
    void superaUmbralSiiElValorAbsolutoDelPorcentajeExcedeElUmbral(
            @ForAll("presupuestadosPositivos") BigDecimal presupuestado,
            @ForAll("reales") BigDecimal real,
            @ForAll("tipos") TipoPresupuesto tipo,
            @ForAll("umbrales") BigDecimal umbral) {

        ResultadoVariacion r = CalculoVariacionPresupuesto.calcular(presupuestado, real, tipo, umbral);

        BigDecimal umbralEnPuntos = umbral.multiply(CIEN);
        boolean esperado = r.variacionPorcentaje().abs().compareTo(umbralEnPuntos) > 0;
        assertThat(r.superaUmbral())
                .as("superaUmbral <=> abs(%s) > umbral*100 (%s)", r.variacionPorcentaje(), umbralEnPuntos)
                .isEqualTo(esperado);
    }
}