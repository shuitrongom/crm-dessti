package com.dessti.crm.rhnomina.nomina.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 19: Identidad
 * aritmetica de la nomina</strong> (Req 41.1, 41.2).
 *
 * <p>Reutiliza la pieza de produccion pura
 * {@link CalculoNomina#calcular(EntradaNomina)} sin base de datos ni contexto de
 * Spring. Cada {@link EntradaNomina} se genera con rangos <em>realistas</em>: salario
 * diario en centavos, dias del periodo en {7, 15, 30}, tiempo extra/aguinaldo/PTU no
 * negativos y acotados, y tasa de Infonavit en {@code [0, 0.20]}. Ese acotamiento de
 * la tasa de Infonavit garantiza que las deducciones (ISR + IMSS + Infonavit) no
 * excedan las percepciones, de modo que el neto sea no negativo (Req 41.1); la tasa
 * completa {@code [0, 1]} la validan las pruebas unitarias y el dominio, no esta
 * propiedad de identidad.</p>
 *
 * <h2>Invariantes verificados (Property 19)</h2>
 * <ul>
 *   <li>{@code percepciones == round(salario + tiempoExtra + aguinaldo + ptu, 2)}.</li>
 *   <li>{@code deducciones  == round(isr + imss + infonavit, 2)}.</li>
 *   <li>{@code neto == round(percepciones - deducciones + subsidio, 2)} (escala 2).</li>
 *   <li>{@code neto >= 0} para las entradas realistas generadas (Req 41.1).</li>
 *   <li>El calculo es puro y determinista: mismas entradas -&gt; mismo resultado.</li>
 * </ul>
 */
class IdentidadAritmeticaNominaPropertyTest {

    private static final int ESCALA = TablasFiscalesNomina.ESCALA_MONETARIA;   // 2

    /** Salario diario maximo expresado en centavos (salario_diario <= 10,000.00). */
    private static final long MAX_SALARIO_DIARIO_CENTAVOS = 1_000_000L;

    /** Salario diario minimo expresado en centavos (salario_diario >= 100.00, > 0). */
    private static final long MIN_SALARIO_DIARIO_CENTAVOS = 10_000L;

    /** Importe maximo (en centavos) para tiempo extra / aguinaldo / PTU (<= 50,000.00). */
    private static final long MAX_IMPORTE_CENTAVOS = 5_000_000L;

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Salario diario con escala 2 exacta en {@code [100.00, 10,000.00]}. */
    @Provide
    Arbitrary<BigDecimal> salariosDiarios() {
        return Arbitraries.longs()
                .between(MIN_SALARIO_DIARIO_CENTAVOS, MAX_SALARIO_DIARIO_CENTAVOS)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
    }

    /** Dias del periodo segun periodicidad: semanal (7), quincenal (15), mensual (30). */
    @Provide
    Arbitrary<Integer> diasPeriodo() {
        return Arbitraries.of(7, 15, 30);
    }

    /** Importe no negativo con escala 2 exacta en {@code [0.00, 50,000.00]}. */
    @Provide
    Arbitrary<BigDecimal> importesNoNegativos() {
        return Arbitraries.longs()
                .between(0L, MAX_IMPORTE_CENTAVOS)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
    }

    /**
     * Tasa de Infonavit en {@code [0, 0.20]} con escala 4, generada a partir de puntos
     * base (0..2000). Se acota a 0.20 para asegurar {@code neto >= 0} con entradas
     * realistas (ver documentacion de la clase).
     */
    @Provide
    Arbitrary<BigDecimal> tasasInfonavit() {
        return Arbitraries.integers()
                .between(0, 2_000)
                .map(puntos -> new BigDecimal(puntos).movePointLeft(4));
    }

    @Provide
    Arbitrary<EntradaNomina> entradas() {
        return Combinators.combine(
                        salariosDiarios(),
                        diasPeriodo(),
                        importesNoNegativos(),
                        importesNoNegativos(),
                        importesNoNegativos(),
                        tasasInfonavit())
                .as(EntradaNomina::new);
    }

    // ----------------------------------------------------------------------
    // Property 19 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 19: Identidad aritmética de la nómina
    @Property(tries = 1000)
    void percepcionesSonLaSumaDeSusComponentes(@ForAll("entradas") EntradaNomina entrada) {
        ResultadoNomina r = CalculoNomina.calcular(entrada);

        BigDecimal esperado = r.salario()
                .add(r.tiempoExtra())
                .add(r.aguinaldo())
                .add(r.ptu())
                .setScale(ESCALA, RoundingMode.HALF_UP);

        assertThat(r.percepciones())
                .as("percepciones == round(salario + tiempoExtra + aguinaldo + ptu, 2)")
                .isEqualByComparingTo(esperado);
        assertThat(r.percepciones().scale()).as("las percepciones tienen escala 2").isEqualTo(ESCALA);
    }

    // Feature: crm-anuncios-luminosos, Property 19: Identidad aritmética de la nómina
    @Property(tries = 1000)
    void deduccionesSonLaSumaDeIsrImssInfonavit(@ForAll("entradas") EntradaNomina entrada) {
        ResultadoNomina r = CalculoNomina.calcular(entrada);

        BigDecimal esperado = r.isr()
                .add(r.imss())
                .add(r.infonavit())
                .setScale(ESCALA, RoundingMode.HALF_UP);

        assertThat(r.deducciones())
                .as("deducciones == round(isr + imss + infonavit, 2)")
                .isEqualByComparingTo(esperado);
        assertThat(r.deducciones().scale()).as("las deducciones tienen escala 2").isEqualTo(ESCALA);
    }

    // Feature: crm-anuncios-luminosos, Property 19: Identidad aritmética de la nómina
    @Property(tries = 1000)
    void netoEsPercepcionesMenosDeduccionesMasSubsidio(@ForAll("entradas") EntradaNomina entrada) {
        ResultadoNomina r = CalculoNomina.calcular(entrada);

        BigDecimal esperado = r.percepciones()
                .subtract(r.deducciones())
                .add(r.subsidio())
                .setScale(ESCALA, RoundingMode.HALF_UP);

        assertThat(r.neto())
                .as("neto == round(percepciones - deducciones + subsidio, 2)")
                .isEqualByComparingTo(esperado);
        assertThat(r.neto().scale()).as("el neto tiene escala 2").isEqualTo(ESCALA);
    }

    // Feature: crm-anuncios-luminosos, Property 19: Identidad aritmética de la nómina
    @Property(tries = 1000)
    void netoNoEsNegativo(@ForAll("entradas") EntradaNomina entrada) {
        ResultadoNomina r = CalculoNomina.calcular(entrada);

        assertThat(r.neto())
                .as("neto >= 0 para entradas realistas (Req 41.1)")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    // Feature: crm-anuncios-luminosos, Property 19: Identidad aritmética de la nómina
    @Property(tries = 1000)
    void calculoDeNominaEsPuroYDeterminista(@ForAll("entradas") EntradaNomina entrada) {
        ResultadoNomina primero = CalculoNomina.calcular(entrada);
        ResultadoNomina segundo = CalculoNomina.calcular(entrada);

        assertThat(segundo.percepciones()).isEqualByComparingTo(primero.percepciones());
        assertThat(segundo.deducciones()).isEqualByComparingTo(primero.deducciones());
        assertThat(segundo.subsidio()).isEqualByComparingTo(primero.subsidio());
        assertThat(segundo.neto())
                .as("mismas entradas -> mismo neto (calculo puro y determinista)")
                .isEqualByComparingTo(primero.neto());
    }
}
