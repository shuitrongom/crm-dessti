package com.dessti.crm.compras.recepcion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.dessti.crm.compras.ordencompra.domain.EstadoOrdenCompra;
import com.dessti.crm.compras.recepcion.domain.ReglasRecepcion.AvancePartida;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 11: Estado de
 * Orden de Compra derivado de las recepciones</strong> (Req 32.5, 32.6).
 *
 * <p>Ejercita directamente la regla de dominio PURA
 * {@link ReglasRecepcion#derivarEstadoOrdenCompra(java.util.Collection)}, sin base
 * de datos ni contexto de Spring: la funcion es estatica, sin estado y
 * determinista, por lo que la derivacion del estado de la Orden_Compra se comprueba
 * universalmente sobre avances arbitrarios por partida.</p>
 *
 * <h2>Invariantes verificados (Property 11)</h2>
 * <ol>
 *   <li>Si TODAS las partidas estan completas ({@code recibida >= ordenada}), el
 *       estado derivado es {@link EstadoOrdenCompra#RECIBIDA_TOTAL} (Req 32.5).</li>
 *   <li>Si alguna partida tiene recepcion pero no todas estan completas, el estado
 *       derivado es {@link EstadoOrdenCompra#RECIBIDA_PARCIAL} (Req 32.6).</li>
 *   <li>Si ninguna partida tiene recepcion (acumulado 0 en todas), el estado
 *       derivado es {@link EstadoOrdenCompra#ABIERTA} (sin cambio).</li>
 * </ol>
 *
 * <h2>Convenciones numericas (espejo de produccion)</h2>
 * <p>Cantidades a escala 3 (coherente con {@code NUMERIC(18,3)} de V29). Las
 * ordenadas se generan estrictamente positivas; el recibido por partida se genera
 * como una fraccion de la ordenada para cubrir los casos vacio/parcial/completo.</p>
 */
class EstadoOrdenCompraDerivadoPropertyTest {

    /** Escala de cantidades espejo de produccion (NUMERIC(18,3)). */
    private static final int ESCALA_CANTIDAD = 3;

    private static BigDecimal aCantidad(long milesimas) {
        return new BigDecimal(milesimas).movePointLeft(ESCALA_CANTIDAD);
    }

    /** Ordenada estrictamente positiva a escala 3 (milesimas de 1 a 10_000_000). */
    @Provide
    Arbitrary<Long> ordenadasMilesimas() {
        return Arbitraries.longs().between(1L, 10_000_000L);
    }

    /** Fraccion en [0, 1] con 4 decimales para escalar el recibido respecto de la ordenada. */
    @Provide
    Arbitrary<BigDecimal> fracciones() {
        return Arbitraries.longs().between(0L, 10_000L)
                .map(diez -> new BigDecimal(diez).movePointLeft(4));
    }

    // ----------------------------------------------------------------------
    // Property 11 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 11: Estado de Orden de Compra derivado de las recepciones
    @Property(tries = 1000)
    void todasCompletasDerivaRecibidaTotal(
            @ForAll @IntRange(min = 1, max = 20) int numPartidas,
            @ForAll("ordenadasMilesimas") long semilla) {

        List<AvancePartida> avances = new ArrayList<>();
        for (int i = 0; i < numPartidas; i++) {
            BigDecimal ordenada = aCantidad(semilla + i + 1L);
            // recibida >= ordenada (aqui exactamente igual): completa.
            avances.add(new AvancePartida(ordenada, ordenada));
        }

        assertThat(ReglasRecepcion.derivarEstadoOrdenCompra(avances))
                .as("todas las partidas completas => recibida_total")
                .isEqualTo(EstadoOrdenCompra.RECIBIDA_TOTAL);
    }

    // Feature: crm-anuncios-luminosos, Property 11: Estado de Orden de Compra derivado de las recepciones
    @Property(tries = 1000)
    void recepcionParcialDerivaRecibidaParcial(
            @ForAll @IntRange(min = 2, max = 20) int numPartidas,
            @ForAll("ordenadasMilesimas") long semilla) {

        // Construir todas completas menos la primera, que queda estrictamente parcial
        // (recibida > 0 y < ordenada). Asi hay recepcion pero no todas completas.
        List<AvancePartida> avances = new ArrayList<>();

        BigDecimal ordenadaPrimera = aCantidad(semilla + 10L); // >= 0.010 para admitir parcial
        // recibida parcial: la mitad de la ordenada, acotada a >= 1 milesima y < ordenada.
        BigDecimal parcial = ordenadaPrimera.divide(BigDecimal.valueOf(2), ESCALA_CANTIDAD,
                RoundingMode.DOWN).max(aCantidad(1L));
        if (parcial.compareTo(ordenadaPrimera) >= 0) {
            // Garantizar estrictamente parcial.
            parcial = ordenadaPrimera.subtract(aCantidad(1L));
        }
        avances.add(new AvancePartida(ordenadaPrimera, parcial));

        for (int i = 1; i < numPartidas; i++) {
            BigDecimal ordenada = aCantidad(semilla + i + 1L);
            avances.add(new AvancePartida(ordenada, ordenada)); // completas
        }

        assertThat(ReglasRecepcion.derivarEstadoOrdenCompra(avances))
                .as("alguna partida con recepcion pero no todas completas => recibida_parcial")
                .isEqualTo(EstadoOrdenCompra.RECIBIDA_PARCIAL);
    }

    // Feature: crm-anuncios-luminosos, Property 11: Estado de Orden de Compra derivado de las recepciones
    @Property(tries = 1000)
    void unaSolaPartidaParcialDerivaRecibidaParcial(
            @ForAll("ordenadasMilesimas") long ordenadaMilesimas) {

        BigDecimal ordenada = aCantidad(Math.max(ordenadaMilesimas, 2L));
        // recibida en (0, ordenada): parcial.
        BigDecimal recibida = ordenada.subtract(aCantidad(1L));
        if (recibida.signum() <= 0) {
            return; // ordenada demasiado pequena para un parcial estricto
        }

        assertThat(ReglasRecepcion.derivarEstadoOrdenCompra(
                List.of(new AvancePartida(ordenada, recibida))))
                .as("unica partida con recepcion parcial => recibida_parcial")
                .isEqualTo(EstadoOrdenCompra.RECIBIDA_PARCIAL);
    }

    // Feature: crm-anuncios-luminosos, Property 11: Estado de Orden de Compra derivado de las recepciones
    @Property(tries = 1000)
    void sinRecepcionEnNingunaPartidaDerivaAbierta(
            @ForAll @IntRange(min = 1, max = 20) int numPartidas,
            @ForAll("ordenadasMilesimas") long semilla) {

        List<AvancePartida> avances = new ArrayList<>();
        for (int i = 0; i < numPartidas; i++) {
            BigDecimal ordenada = aCantidad(semilla + i + 1L);
            avances.add(new AvancePartida(ordenada, BigDecimal.ZERO)); // sin recepcion
        }

        assertThat(ReglasRecepcion.derivarEstadoOrdenCompra(avances))
                .as("ninguna partida con recepcion => abierta (sin cambio)")
                .isEqualTo(EstadoOrdenCompra.ABIERTA);
    }
}
