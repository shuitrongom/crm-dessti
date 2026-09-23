package com.dessti.crm.comercial.cotizacion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 2: Totales
 * monetarios de documentos con partidas</strong> (Req 6.3, 6.5, 31.3, 31.5).
 *
 * <p>Reutiliza las piezas de produccion puras
 * {@link CotizacionValidaciones#calcularSubtotalPartida(int, BigDecimal)} y
 * {@link CotizacionValidaciones#normalizarMonto(BigDecimal)}, sin base de datos
 * ni contexto de Spring. La aritmetica de totales de un documento con partidas
 * se modela en memoria (misma agregacion que {@code Cotizacion.crear}: subtotal
 * por partida via {@code calcularSubtotalPartida} y total como
 * {@code normalizarMonto(Σ subtotales)}), lo que permite ejercitar el invariante
 * universalmente sin construir entidades JPA completas.</p>
 *
 * <h2>Invariantes verificados (Property 2)</h2>
 * <ul>
 *   <li>El subtotal de cada partida es exactamente
 *       {@code round(cantidad × precio_unitario, 2, HALF_UP)}, con escala 2.</li>
 *   <li>El total del documento es exactamente
 *       {@code round(Σ subtotales, 2, HALF_UP)}, con escala 2.</li>
 *   <li>El total esta acotado por los limites teoricos del conjunto de partidas
 *       (>= suma de minimos, <= suma de maximos) y nunca es negativo.</li>
 *   <li>El calculo es puro y determinista: las mismas entradas producen siempre
 *       el mismo resultado.</li>
 * </ul>
 */
class TotalesMonetariosPropertyTest {

    private static final int ESCALA = CotizacionValidaciones.ESCALA_MONETARIA;   // 2
    private static final int CANTIDAD_MINIMA = CotizacionValidaciones.CANTIDAD_MINIMA;   // 1
    private static final int CANTIDAD_MAXIMA = CotizacionValidaciones.CANTIDAD_MAXIMA;   // 999_999
    private static final BigDecimal PRECIO_MINIMO = CotizacionValidaciones.PRECIO_MINIMO;   // 0.01
    private static final BigDecimal PRECIO_MAXIMO = CotizacionValidaciones.PRECIO_MAXIMO;   // 999999999.99

    /** Precio maximo expresado en centavos: 999,999,999.99 == 99_999_999_999 centavos. */
    private static final long MAX_CENTAVOS = 99_999_999_999L;

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Una partida valida: cantidad entera en {@code [1, 999,999]} y precio
     * unitario con escala 2 exacta, generado a partir de un numero entero de
     * centavos en {@code [1, 99_999_999_999]} y dividido entre 100, cubriendo
     * uniformemente todo {@code [0.01, 999,999,999.99]}.
     */
    @Provide
    Arbitrary<Partida> partidas() {
        Arbitrary<Integer> cantidades = Arbitraries.integers()
                .between(CANTIDAD_MINIMA, CANTIDAD_MAXIMA);
        Arbitrary<BigDecimal> precios = Arbitraries.longs()
                .between(1L, MAX_CENTAVOS)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
        return Combinators.combine(cantidades, precios).as(Partida::new);
    }

    /** Documento con entre 1 y 20 partidas validas (cubre el invariante de agregacion). */
    @Provide
    Arbitrary<List<Partida>> documentos() {
        return partidas().list().ofMinSize(1).ofMaxSize(20);
    }

    // ----------------------------------------------------------------------
    // Property 2 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 2: Para cualquier documento con partidas (Cotizacion u Orden_Compra) con cantidades en 1..999,999 y precios unitarios en 0.01..999,999,999.99, el subtotal de cada partida es igual a round(cantidad × precio_unitario, 2) y el total del documento es igual a round(Σ subtotales, 2), con redondeo al valor más cercano y aritmética decimal (NUMERIC).
    @Property(tries = 1000)
    void subtotalDePartidaEsProductoRedondeadoHalfUpAEscala2(@ForAll("partidas") Partida partida) {
        BigDecimal subtotal =
                CotizacionValidaciones.calcularSubtotalPartida(partida.cantidad, partida.precioUnitario);

        BigDecimal esperado = partida.precioUnitario
                .multiply(BigDecimal.valueOf(partida.cantidad))
                .setScale(ESCALA, RoundingMode.HALF_UP);

        assertThat(subtotal)
                .as("subtotal == round(cantidad × precio_unitario, 2, HALF_UP)")
                .isEqualByComparingTo(esperado);
        assertThat(subtotal.scale())
                .as("el subtotal debe tener escala 2")
                .isEqualTo(ESCALA);
        assertThat(subtotal.signum())
                .as("el subtotal de una partida valida nunca es negativo")
                .isGreaterThan(0);
    }

    // Feature: crm-anuncios-luminosos, Property 2: Para cualquier documento con partidas (Cotizacion u Orden_Compra) con cantidades en 1..999,999 y precios unitarios en 0.01..999,999,999.99, el subtotal de cada partida es igual a round(cantidad × precio_unitario, 2) y el total del documento es igual a round(Σ subtotales, 2), con redondeo al valor más cercano y aritmética decimal (NUMERIC).
    @Property(tries = 1000)
    void totalDelDocumentoEsSumaDeSubtotalesRedondeadaHalfUpAEscala2(
            @ForAll("documentos") List<Partida> documento) {
        BigDecimal suma = BigDecimal.ZERO;
        for (Partida partida : documento) {
            suma = suma.add(
                    CotizacionValidaciones.calcularSubtotalPartida(partida.cantidad, partida.precioUnitario));
        }
        BigDecimal total = CotizacionValidaciones.normalizarMonto(suma);

        BigDecimal esperado = suma.setScale(ESCALA, RoundingMode.HALF_UP);

        assertThat(total)
                .as("total == round(Σ subtotales, 2, HALF_UP)")
                .isEqualByComparingTo(esperado);
        assertThat(total.scale())
                .as("el total debe tener escala 2")
                .isEqualTo(ESCALA);
    }

    // Feature: crm-anuncios-luminosos, Property 2: Para cualquier documento con partidas (Cotizacion u Orden_Compra) con cantidades en 1..999,999 y precios unitarios en 0.01..999,999,999.99, el subtotal de cada partida es igual a round(cantidad × precio_unitario, 2) y el total del documento es igual a round(Σ subtotales, 2), con redondeo al valor más cercano y aritmética decimal (NUMERIC).
    @Property(tries = 1000)
    void totalEstaAcotadoPorLosLimitesTeoricosDelConjuntoDePartidas(
            @ForAll("documentos") List<Partida> documento) {
        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal minimoPosible = BigDecimal.ZERO;
        BigDecimal maximoPosible = BigDecimal.ZERO;
        for (Partida partida : documento) {
            suma = suma.add(
                    CotizacionValidaciones.calcularSubtotalPartida(partida.cantidad, partida.precioUnitario));
            // Con cada partida, el minimo posible es 1 × 0.01 y el maximo N × 999,999,999.99.
            minimoPosible = minimoPosible.add(PRECIO_MINIMO);
            maximoPosible = maximoPosible.add(
                    PRECIO_MAXIMO.multiply(BigDecimal.valueOf(CANTIDAD_MAXIMA)));
        }
        BigDecimal total = CotizacionValidaciones.normalizarMonto(suma);

        assertThat(total)
                .as("el total nunca es negativo")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(total)
                .as("el total >= suma de minimos (todas las partidas 1 × 0.01)")
                .isGreaterThanOrEqualTo(minimoPosible.setScale(ESCALA, RoundingMode.HALF_UP));
        assertThat(total)
                .as("el total <= suma de maximos (todas las partidas 999,999 × 999,999,999.99)")
                .isLessThanOrEqualTo(maximoPosible.setScale(ESCALA, RoundingMode.HALF_UP));
        assertThat(total.scale())
                .as("el total debe tener escala 2")
                .isEqualTo(ESCALA);
    }

    // Feature: crm-anuncios-luminosos, Property 2: Para cualquier documento con partidas (Cotizacion u Orden_Compra) con cantidades en 1..999,999 y precios unitarios en 0.01..999,999,999.99, el subtotal de cada partida es igual a round(cantidad × precio_unitario, 2) y el total del documento es igual a round(Σ subtotales, 2), con redondeo al valor más cercano y aritmética decimal (NUMERIC).
    @Property(tries = 1000)
    void calculoDeTotalesEsPuroYDeterminista(@ForAll("documentos") List<Partida> documento) {
        BigDecimal primeraSuma = BigDecimal.ZERO;
        BigDecimal segundaSuma = BigDecimal.ZERO;
        for (Partida partida : documento) {
            primeraSuma = primeraSuma.add(
                    CotizacionValidaciones.calcularSubtotalPartida(partida.cantidad, partida.precioUnitario));
            // Segunda evaluacion independiente con las mismas entradas.
            segundaSuma = segundaSuma.add(
                    CotizacionValidaciones.calcularSubtotalPartida(partida.cantidad, partida.precioUnitario));
        }
        BigDecimal totalPrimero = CotizacionValidaciones.normalizarMonto(primeraSuma);
        BigDecimal totalSegundo = CotizacionValidaciones.normalizarMonto(segundaSuma);

        assertThat(totalSegundo)
                .as("mismas entradas -> mismo total (calculo puro y determinista)")
                .isEqualByComparingTo(totalPrimero);
    }

    /**
     * Modelo en memoria de una partida valida (cantidad y precio unitario ya
     * dentro de rango), para ejercitar la aritmetica de totales sin entidades JPA.
     */
    private static final class Partida {
        private final int cantidad;
        private final BigDecimal precioUnitario;

        private Partida(int cantidad, BigDecimal precioUnitario) {
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
        }
    }
}
