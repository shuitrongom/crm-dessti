package com.dessti.crm.estrategia.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 29: Avance de
 * Objetivo_Estrategico acotado y ponderado</strong> (Req 58.8, 58.9; tarea 37.2).
 *
 * <p>Ejercen directamente la funcion pura del dominio
 * {@link CalculoAvanceObjetivo#avancePonderado(List)} sobre listas arbitrarias de
 * {@link ResultadoClaveValor} (incluidos casos con {@code valorActual} muy superior
 * al {@code valorObjetivo}). No hay base de datos ni contexto de Spring: la funcion
 * es una pieza pura y determinista.</p>
 *
 * <p>Las propiedades comprueban universalmente que:</p>
 * <ul>
 *   <li>el avance ponderado esta SIEMPRE en [0, 100] y NUNCA excede 100%, aunque el
 *       valor actual supere ampliamente al objetivo (Req 58.9);</li>
 *   <li>para entradas dentro de rango (valor actual &lt;= objetivo) coincide con el
 *       promedio ponderado {@code sum(pesoÂ·actual/objetivo)/sum(peso)Â·100} redondeado
 *       a 2 decimales (Req 58.8);</li>
 *   <li>una lista vacia de resultados clave produce avance 0;</li>
 *   <li>si todos los resultados cumplen o superan su objetivo, el avance es
 *       exactamente 100.00 (tope de la ponderacion).</li>
 * </ul>
 */
class AvanceObjetivoAcotadoPropertyTest {

    /** Precision de referencia para recomputar el promedio ponderado esperado. */
    private static final MathContext PRECISION = new MathContext(20, RoundingMode.HALF_UP);

    private static final BigDecimal CERO = new BigDecimal("0.00");
    private static final BigDecimal CIEN = new BigDecimal("100.00");

    // ----------------------------------------------------------------------
    // Property 29 â€” Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 29: El avance de un Objetivo_Estrategico es el porcentaje ponderado de cumplimiento de sus resultados clave, siempre acotado a [0,100] y nunca superior a 100%.
    @Property(tries = 1000)
    void avanceSiempreAcotadoAUnRangoCerradoCienPorCiento(
            @ForAll("resultadosArbitrarios") @Size(min = 1, max = 12) List<ResultadoClaveValor> resultados) {

        BigDecimal avance = CalculoAvanceObjetivo.avancePonderado(resultados);

        assertThat(avance)
                .as("el avance ponderado nunca es negativo (>= 0%)")
                .isGreaterThanOrEqualTo(CERO)
                .as("el avance ponderado nunca excede 100% (Req 58.9)")
                .isLessThanOrEqualTo(CIEN);
        assertThat(avance.scale())
                .as("el avance se expresa con escala 2 (porcentaje)")
                .isEqualTo(2);
    }

    // Feature: crm-anuncios-luminosos, Property 29: El avance de un Objetivo_Estrategico es el porcentaje ponderado de cumplimiento de sus resultados clave, siempre acotado a [0,100] y nunca superior a 100%.
    @Property(tries = 1000)
    void avanceCoincideConElPromedioPonderadoParaEntradasEnRango(
            @ForAll("resultadosEnRango") @Size(min = 1, max = 12) List<ResultadoClaveValor> resultados) {

        BigDecimal avance = CalculoAvanceObjetivo.avancePonderado(resultados);

        // Referencia: cumplimiento_i = actual/objetivo (en [0,1] por construccion del
        // generador), promedio ponderado por peso, expresado en porcentaje y redondeado
        // a 2 decimales half-up (Req 58.8).
        BigDecimal sumaPesos = BigDecimal.ZERO;
        BigDecimal sumaPonderada = BigDecimal.ZERO;
        for (ResultadoClaveValor rc : resultados) {
            BigDecimal cumplimiento = rc.valorActual().divide(rc.valorObjetivo(), PRECISION);
            sumaPesos = sumaPesos.add(rc.peso());
            sumaPonderada = sumaPonderada.add(rc.peso().multiply(cumplimiento));
        }
        BigDecimal esperado = sumaPonderada
                .divide(sumaPesos, PRECISION)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        assertThat(avance)
                .as("el avance coincide con el promedio ponderado de cumplimiento (Req 58.8)")
                .isEqualByComparingTo(esperado);
    }

    // Feature: crm-anuncios-luminosos, Property 29: El avance de un Objetivo_Estrategico es el porcentaje ponderado de cumplimiento de sus resultados clave, siempre acotado a [0,100] y nunca superior a 100%.
    @Property(tries = 1000)
    void avanceEsCienCuandoTodosLosResultadosAlcanzanOSuperanSuObjetivo(
            @ForAll("resultadosCumplidos") @Size(min = 1, max = 12) List<ResultadoClaveValor> resultados) {

        BigDecimal avance = CalculoAvanceObjetivo.avancePonderado(resultados);

        assertThat(avance)
                .as("si todos los resultados cumplen o superan su objetivo, el avance es 100% (tope)")
                .isEqualByComparingTo(CIEN);
    }

    // Feature: crm-anuncios-luminosos, Property 29: El avance de un Objetivo_Estrategico es el porcentaje ponderado de cumplimiento de sus resultados clave, siempre acotado a [0,100] y nunca superior a 100%.
    @Property(tries = 1000)
    void avanceEsCeroSinResultadosClave(@ForAll("relleno") int ignorado) {
        BigDecimal avance = CalculoAvanceObjetivo.avancePonderado(new ArrayList<>());
        assertThat(avance)
                .as("sin resultados clave el avance ponderado es 0%")
                .isEqualByComparingTo(CERO);
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Metrica no negativa con escala 4 en un rango amplio (0 .. 1_000_000). */
    private Arbitrary<BigDecimal> metricaNoNegativa() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("1000000"))
                .ofScale(4);
    }

    /** Valor objetivo estrictamente positivo (escala 4). */
    private Arbitrary<BigDecimal> valorObjetivoPositivo() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.0001"), new BigDecimal("1000000"))
                .ofScale(4)
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0);
    }

    /** Peso estrictamente positivo en (0, 100] (escala 2). */
    private Arbitrary<BigDecimal> pesoPositivo() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("100"))
                .ofScale(2)
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * Resultados arbitrarios: valor actual no negativo cualquiera (puede superar
     * ampliamente al objetivo), objetivo positivo y peso positivo. Ejerce el
     * acotamiento superior del cumplimiento (Req 58.9).
     */
    @Provide
    Arbitrary<List<ResultadoClaveValor>> resultadosArbitrarios() {
        Arbitrary<ResultadoClaveValor> rc = Combinators
                .combine(metricaNoNegativa(), valorObjetivoPositivo(), pesoPositivo())
                .as(ResultadoClaveValor::new);
        return rc.list().ofMinSize(1).ofMaxSize(12);
    }

    /** Fraccion de cumplimiento en [0, 1] con escala 6, para derivar el valor actual. */
    private Arbitrary<BigDecimal> fraccionEnRango() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.ONE)
                .ofScale(6);
    }

    /** Factor de sobrecumplimiento en [1, 5] con escala 4, para superar el objetivo. */
    private Arbitrary<BigDecimal> factorSobrecumplimiento() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ONE, new BigDecimal("5"))
                .ofScale(4);
    }

    /**
     * Resultados dentro de rango: el valor actual se construye como
     * {@code objetivo Â· fraccion} con {@code fraccion âˆˆ [0, 1]}, de modo que el valor
     * actual nunca supera al objetivo, cada cumplimiento cae en [0, 1] y el avance
     * coincide exactamente con el promedio ponderado (sin activar el acotamiento
     * superior). Se genera con el valor actual ya derivado a escala 4.
     */
    @Provide
    Arbitrary<List<ResultadoClaveValor>> resultadosEnRango() {
        Arbitrary<ResultadoClaveValor> rc = Combinators
                .combine(valorObjetivoPositivo(), pesoPositivo(), fraccionEnRango())
                .as((objetivo, peso, fraccion) -> {
                    BigDecimal actual = objetivo.multiply(fraccion)
                            .setScale(4, RoundingMode.HALF_UP);
                    // Garantiza actual <= objetivo tras el redondeo (defensa del generador).
                    if (actual.compareTo(objetivo) > 0) {
                        actual = objetivo;
                    }
                    return new ResultadoClaveValor(actual, objetivo, peso);
                });
        return rc.list().ofMinSize(1).ofMaxSize(12);
    }

    /**
     * Resultados que cumplen o superan su objetivo: el valor actual se construye como
     * {@code objetivo Â· factor} con {@code factor >= 1}, de modo que el valor actual es
     * siempre &gt;= objetivo, cada cumplimiento se acota a 1 y el avance total es 100%.
     */
    @Provide
    Arbitrary<List<ResultadoClaveValor>> resultadosCumplidos() {
        Arbitrary<ResultadoClaveValor> rc = Combinators
                .combine(valorObjetivoPositivo(), pesoPositivo(), factorSobrecumplimiento())
                .as((objetivo, peso, factor) -> {
                    BigDecimal actual = objetivo.multiply(factor)
                            .setScale(4, RoundingMode.HALF_UP);
                    // Garantiza actual >= objetivo tras el redondeo (defensa del generador).
                    if (actual.compareTo(objetivo) < 0) {
                        actual = objetivo;
                    }
                    return new ResultadoClaveValor(actual, objetivo, peso);
                });
        return rc.list().ofMinSize(1).ofMaxSize(12);
    }

    @Provide
    Arbitrary<Integer> relleno() {
        return Arbitraries.integers().between(0, 1);
    }
}
