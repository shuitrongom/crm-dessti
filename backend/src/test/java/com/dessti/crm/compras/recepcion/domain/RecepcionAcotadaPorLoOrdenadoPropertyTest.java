package com.dessti.crm.compras.recepcion.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 10: Recepcion de
 * mercancia acotada por lo ordenado</strong> (Req 32.3).
 *
 * <p>Ejercita directamente la regla de dominio PURA
 * {@link ReglasRecepcion#validarNoExcederOrdenado(BigDecimal, BigDecimal, BigDecimal)},
 * sin base de datos ni contexto de Spring: la funcion es estatica, sin estado y
 * determinista, por lo que la invariante "la cantidad recibida acumulada nunca
 * excede la cantidad ordenada" se comprueba universalmente sobre entradas
 * arbitrarias.</p>
 *
 * <h2>Invariantes verificados (Property 10)</h2>
 * <ol>
 *   <li>Con {@code previa + nueva <= ordenada} y {@code nueva > 0}, la validacion
 *       ACEPTA (no lanza).</li>
 *   <li>Con {@code previa + nueva > ordenada}, la validacion RECHAZA con
 *       {@link ReglaNegocioException} (nunca permite exceder lo ordenado).</li>
 * </ol>
 *
 * <h2>Convenciones numericas (espejo de produccion)</h2>
 * <p>Cantidades a escala 3 (coherente con {@code NUMERIC(18,3)} de V29),
 * construidas a partir de enteros escalados (milesimas) para mantener
 * comparaciones exactas por {@code compareTo}.</p>
 */
class RecepcionAcotadaPorLoOrdenadoPropertyTest {

    /** Escala de cantidades espejo de produccion (NUMERIC(18,3)). */
    private static final int ESCALA_CANTIDAD = 3;

    /** Cantidad &gt;= 0 a escala 3, en milesimas de 0 a 100_000_000 (0..100000). */
    private static BigDecimal aCantidad(long milesimas) {
        return new BigDecimal(milesimas).movePointLeft(ESCALA_CANTIDAD);
    }

    /** Ordenada estrictamente positiva a escala 3 (milesimas de 1 a 100_000_000). */
    @Provide
    Arbitrary<Long> ordenadasMilesimas() {
        return Arbitraries.longs().between(1L, 100_000_000L);
    }

    /** Fraccion en [0, 1] con 4 decimales, para repartir la ordenada en previa/nueva. */
    @Provide
    Arbitrary<BigDecimal> fracciones() {
        return Arbitraries.longs().between(0L, 10_000L)
                .map(diez -> new BigDecimal(diez).movePointLeft(4));
    }

    /** Exceso estrictamente positivo a escala 3 (milesimas de 1 a 1_000_000). */
    @Provide
    Arbitrary<Long> excesosMilesimas() {
        return Arbitraries.longs().between(1L, 1_000_000L);
    }

    // ----------------------------------------------------------------------
    // Property 10 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 10: Recepción de mercancía acotada por lo ordenado
    @Property(tries = 1000)
    void dentroDeLoOrdenadoLaRecepcionSeAcepta(
            @ForAll("ordenadasMilesimas") long ordenadaMilesimas,
            @ForAll("fracciones") BigDecimal fraccionPrevia,
            @ForAll("fracciones") BigDecimal fraccionNueva) {

        BigDecimal ordenada = aCantidad(ordenadaMilesimas);

        // Repartir la ordenada en previa y nueva de modo que previa + nueva <= ordenada
        // y nueva > 0: previa = ordenada * fPrevia (acotada), nueva = restante * fNueva
        // acotada a > 0.
        BigDecimal previa = ordenada.multiply(fraccionPrevia)
                .setScale(ESCALA_CANTIDAD, java.math.RoundingMode.DOWN)
                .min(ordenada);
        BigDecimal restante = ordenada.subtract(previa);
        BigDecimal nueva = restante.multiply(fraccionNueva)
                .setScale(ESCALA_CANTIDAD, java.math.RoundingMode.DOWN);
        // Garantizar nueva > 0 sin exceder el restante: si quedo 0 y hay restante, usar
        // la milesima minima; si no hay restante, no hay caso valido -> se omite.
        if (nueva.signum() == 0) {
            if (restante.signum() == 0) {
                return; // no queda margen para una nueva recepcion positiva
            }
            nueva = aCantidad(1L).min(restante);
            if (nueva.signum() == 0) {
                return;
            }
        }

        final BigDecimal previaFinal = previa;
        final BigDecimal nuevaFinal = nueva;
        assertThatCode(() -> ReglasRecepcion.validarNoExcederOrdenado(ordenada, previaFinal, nuevaFinal))
                .as("previa(%s) + nueva(%s) <= ordenada(%s) debe aceptarse",
                        previaFinal, nuevaFinal, ordenada)
                .doesNotThrowAnyException();
    }

    // Feature: crm-anuncios-luminosos, Property 10: Recepción de mercancía acotada por lo ordenado
    @Property(tries = 1000)
    void excederLoOrdenadoSiempreSeRechaza(
            @ForAll("ordenadasMilesimas") long ordenadaMilesimas,
            @ForAll("fracciones") BigDecimal fraccionPrevia,
            @ForAll("excesosMilesimas") long excesoMilesimas) {

        BigDecimal ordenada = aCantidad(ordenadaMilesimas);
        BigDecimal previa = ordenada.multiply(fraccionPrevia)
                .setScale(ESCALA_CANTIDAD, java.math.RoundingMode.DOWN)
                .min(ordenada);
        // nueva = (ordenada - previa) + exceso -> garantiza previa + nueva > ordenada.
        BigDecimal exceso = aCantidad(excesoMilesimas);
        BigDecimal nueva = ordenada.subtract(previa).add(exceso);

        assertThatThrownBy(() -> ReglasRecepcion.validarNoExcederOrdenado(ordenada, previa, nueva))
                .as("previa(%s) + nueva(%s) > ordenada(%s) debe rechazarse", previa, nueva, ordenada)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("excede");
    }

    // Feature: crm-anuncios-luminosos, Property 10: Recepción de mercancía acotada por lo ordenado
    @Property(tries = 1000)
    void aceptaJustoHastaElLimiteYRechazaUnaMilesimaMas(
            @ForAll("ordenadasMilesimas") long ordenadaMilesimas,
            @ForAll("fracciones") BigDecimal fraccionPrevia) {

        BigDecimal ordenada = aCantidad(ordenadaMilesimas);
        BigDecimal previa = ordenada.multiply(fraccionPrevia)
                .setScale(ESCALA_CANTIDAD, java.math.RoundingMode.DOWN)
                .min(ordenada);
        BigDecimal restante = ordenada.subtract(previa);
        if (restante.signum() <= 0) {
            return; // sin margen; caso cubierto por la property de exceso
        }

        // Justo en el limite (previa + restante == ordenada): se acepta.
        assertThatCode(() -> ReglasRecepcion.validarNoExcederOrdenado(ordenada, previa, restante))
                .as("recibir exactamente el restante alcanza el limite y se acepta")
                .doesNotThrowAnyException();

        // Una milesima mas que el restante: se rechaza.
        BigDecimal unaMilesimaMas = restante.add(aCantidad(1L));
        assertThatThrownBy(() -> ReglasRecepcion.validarNoExcederOrdenado(ordenada, previa, unaMilesimaMas))
                .as("recibir una milesima por encima del restante excede lo ordenado")
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 10: Recepción de mercancía acotada por lo ordenado
    @Property(tries = 500)
    void cantidadNuevaNoPositivaSeRechaza(
            @ForAll("ordenadasMilesimas") long ordenadaMilesimas,
            @ForAll("fracciones") BigDecimal fraccionPrevia) {

        BigDecimal ordenada = aCantidad(ordenadaMilesimas);
        BigDecimal previa = ordenada.multiply(fraccionPrevia)
                .setScale(ESCALA_CANTIDAD, java.math.RoundingMode.DOWN)
                .min(ordenada);

        assertThatThrownBy(() -> ReglasRecepcion.validarNoExcederOrdenado(ordenada, previa, BigDecimal.ZERO))
                .as("una cantidad nueva de 0 debe rechazarse")
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> ReglasRecepcion.validarNoExcederOrdenado(
                ordenada, previa, aCantidad(1L).negate()))
                .as("una cantidad nueva negativa debe rechazarse")
                .isInstanceOf(ReglaNegocioException.class);
    }
}
