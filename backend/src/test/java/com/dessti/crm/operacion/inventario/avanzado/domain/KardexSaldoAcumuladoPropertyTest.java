package com.dessti.crm.operacion.inventario.avanzado.domain;

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
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 34: Kardex refleja
 * el saldo acumulado con signo</strong> (Req 60.10, 60.12).
 *
 * <p>Modela una secuencia cronologica de movimientos aplicados a traves del motor de
 * costeo PURO {@link MotorCosteo}, encadenando el estado (saldo, promedio y capas) de
 * un movimiento al siguiente, tal como lo haria el servicio al proyectar el Kardex.
 * Cada movimiento es una ENTRADA (cantidad &gt; 0, costo &gt;= 0) o una SALIDA
 * (cantidad &gt; 0).</p>
 *
 * <h2>Decision de generacion</h2>
 * <p>Una SALIDA que excederia el saldo vigente se ACOTA (clamp) al saldo disponible
 * para que sea siempre aplicable; si el saldo vigente es 0 la salida se OMITE. Asi la
 * secuencia siempre respeta la no negatividad del inventario perpetuo y ejercita el
 * saldo acumulado con signo sin depender del rechazo (cubierto por la Property 31).</p>
 *
 * <h2>Invariantes verificados (Property 34)</h2>
 * <ol>
 *   <li><strong>Saldo acumulado con signo:</strong> el saldo tras cada movimiento es
 *       {@code saldoPrevio + cantidad} (entrada) o {@code saldoPrevio - cantidad}
 *       (salida), escala 3.</li>
 *   <li><strong>No negatividad:</strong> {@code nuevoSaldoCantidad()} nunca es negativo
 *       en toda la secuencia.</li>
 *   <li><strong>Fila de Kardex consistente:</strong> {@code costoTotalMovimiento() >= 0}
 *       en todo movimiento y, para entradas, {@code costoTotal == cantidad * costo}
 *       (escala 4); cada fila lleva cantidad, costo y saldo resultante coherentes.</li>
 * </ol>
 *
 * <p>Cubre ambos metodos ({@link MetodoCosteo#PROMEDIO} y {@link MetodoCosteo#PEPS}).
 * Las comparaciones de {@link BigDecimal} usan {@code compareTo}.</p>
 */
class KardexSaldoAcumuladoPropertyTest {

    private static final int ESCALA_CANTIDAD = MotorCosteo.ESCALA_CANTIDAD; // 3
    private static final int ESCALA_COSTO = MotorCosteo.ESCALA_COSTO; // 4

    private static final BigDecimal CERO_CANTIDAD =
            BigDecimal.ZERO.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
    private static final BigDecimal CERO_COSTO =
            BigDecimal.ZERO.setScale(ESCALA_COSTO, RoundingMode.HALF_UP);

    // ----------------------------------------------------------------------
    // Modelo de un movimiento generado
    // ----------------------------------------------------------------------

    /** Tipo de movimiento del Kardex. */
    private enum Tipo { ENTRADA, SALIDA }

    /** Movimiento generado: tipo, cantidad positiva y costo (solo relevante en entradas). */
    private record MovimientoGenerado(Tipo tipo, BigDecimal cantidad, BigDecimal costo) {
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Cantidad estrictamente positiva a escala 3 (0.001..10000). */
    private static Arbitrary<BigDecimal> cantidadPositiva() {
        return Arbitraries.longs()
                .between(1L, 10_000_000L)
                .map(m -> new BigDecimal(m).movePointLeft(ESCALA_CANTIDAD));
    }

    /** Costo unitario &gt;= 0 a escala 4 (0..10000). */
    private static Arbitrary<BigDecimal> costoNoNegativo() {
        return Arbitraries.longs()
                .between(0L, 100_000_000L)
                .map(m -> new BigDecimal(m).movePointLeft(ESCALA_COSTO));
    }

    /** Un movimiento generado; sesgo hacia entradas para que existan existencias que consumir. */
    private Arbitrary<MovimientoGenerado> movimiento() {
        Arbitrary<MovimientoGenerado> entrada =
                Combinators.combine(cantidadPositiva(), costoNoNegativo())
                        .as((c, costo) -> new MovimientoGenerado(Tipo.ENTRADA, c, costo));
        Arbitrary<MovimientoGenerado> salida = cantidadPositiva()
                .map(c -> new MovimientoGenerado(Tipo.SALIDA, c, BigDecimal.ZERO));
        return Arbitraries.oneOf(entrada, entrada, salida);
    }

    /** Secuencia de 1..25 movimientos generados. */
    @Provide
    Arbitrary<List<MovimientoGenerado>> secuencias() {
        return movimiento().list().ofMinSize(1).ofMaxSize(25);
    }

    /** Ambos metodos de costeo. */
    @Provide
    Arbitrary<MetodoCosteo> metodos() {
        return Arbitraries.of(MetodoCosteo.class);
    }

    // ----------------------------------------------------------------------
    // Property 34 — Invariante
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 34: Kardex refleja el saldo acumulado con signo
    @Property(tries = 1000)
    void secuenciaDeMovimientosMantieneSaldoAcumuladoConSignoYNoNegatividad(
            @ForAll("metodos") MetodoCosteo metodo,
            @ForAll("secuencias") List<MovimientoGenerado> movimientos) {

        // Estado inicial: almacen vacio.
        BigDecimal saldo = CERO_CANTIDAD;
        BigDecimal promedio = CERO_COSTO;
        List<CapaCostoValor> capas = List.of();

        for (MovimientoGenerado mov : movimientos) {
            BigDecimal saldoAntes = saldo;

            if (mov.tipo() == Tipo.ENTRADA) {
                ResultadoEntrada r = MotorCosteo.aplicarEntrada(
                        metodo, saldo, promedio, capas, mov.cantidad(), mov.costo());

                // (1) Saldo acumulado con signo: saldoAntes + cantidad.
                BigDecimal esperado = saldoAntes.add(mov.cantidad())
                        .setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
                assertThat(r.nuevoSaldoCantidad())
                        .as("ENTRADA: saldo == saldoAntes(%s) + cantidad(%s) [%s]",
                                saldoAntes, mov.cantidad(), metodo)
                        .isEqualByComparingTo(esperado);

                // (3) La fila de entrada lleva costoTotal == cantidad * costo.
                BigDecimal costoTotalEsperado = mov.cantidad().multiply(mov.costo())
                        .setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
                assertThat(r.costoTotalMovimiento())
                        .as("ENTRADA: costoTotal == cantidad(%s) * costo(%s)",
                                mov.cantidad(), mov.costo())
                        .isEqualByComparingTo(costoTotalEsperado);
                assertThat(r.costoTotalMovimiento())
                        .as("ENTRADA: el costo total del movimiento nunca es negativo")
                        .isGreaterThanOrEqualTo(CERO_COSTO);

                saldo = r.nuevoSaldoCantidad();
                promedio = r.nuevoCostoPromedio();
                capas = r.nuevasCapas();

            } else {
                // SALIDA: acotar al saldo vigente; si el saldo es 0, omitir el movimiento.
                if (saldoAntes.signum() <= 0) {
                    continue;
                }
                BigDecimal cantidadSalida = mov.cantidad().min(saldoAntes)
                        .setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
                if (cantidadSalida.signum() <= 0) {
                    continue;
                }

                ResultadoSalida r = MotorCosteo.aplicarSalida(
                        metodo, saldo, promedio, capas, cantidadSalida);

                // (1) Saldo acumulado con signo: saldoAntes - cantidad.
                BigDecimal esperado = saldoAntes.subtract(cantidadSalida)
                        .setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
                assertThat(r.nuevoSaldoCantidad())
                        .as("SALIDA: saldo == saldoAntes(%s) - cantidad(%s) [%s]",
                                saldoAntes, cantidadSalida, metodo)
                        .isEqualByComparingTo(esperado);

                // (3) La fila de salida lleva un costo total no negativo.
                assertThat(r.costoTotalMovimiento())
                        .as("SALIDA: el costo total del movimiento nunca es negativo")
                        .isGreaterThanOrEqualTo(CERO_COSTO);

                saldo = r.nuevoSaldoCantidad();
                promedio = r.nuevoCostoPromedio();
                capas = r.nuevasCapas();
            }

            // (2) No negatividad en toda la secuencia.
            assertThat(saldo)
                    .as("el saldo acumulado del Kardex nunca es negativo")
                    .isGreaterThanOrEqualTo(CERO_CANTIDAD);
        }
    }
}
