package com.dessti.crm.operacion.inventario.avanzado.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 32: Transferencia
 * entre Almacenes conserva cantidad y costo</strong> (Req 60.10, 60.11).
 *
 * <p>Modela una transferencia entre dos almacenes a nivel del motor de costeo PURO
 * {@link MotorCosteo}: una SALIDA del almacen origen produce un costo unitario del
 * movimiento {@code cu}; ese mismo {@code cu} se usa como costo unitario de la
 * ENTRADA al almacen destino, por la MISMA cantidad. La transferencia no crea ni
 * destruye valor sobre la cantidad movida.</p>
 *
 * <h2>Invariantes verificados (Property 32)</h2>
 * <ol>
 *   <li><strong>Conservacion de cantidad:</strong> el saldo del origen disminuye en
 *       {@code cantidad} y el saldo del destino aumenta en la MISMA {@code cantidad}
 *       (escala 3).</li>
 *   <li><strong>Conservacion de costo:</strong> la entrada al destino usa como costo
 *       unitario el {@code costoUnitarioMovimiento} de la salida del origen; el motor
 *       lo refleja tal cual en {@code costoUnitarioMovimiento} de la entrada
 *       (escala 4).</li>
 *   <li><strong>Costo total conservado:</strong> el {@code costoTotalMovimiento} de la
 *       entrada al destino equivale a {@code cu * cantidad} (escala 4), coherente con
 *       el costo total de la salida del origen dentro del redondeo.</li>
 * </ol>
 *
 * <p>El estado del origen se construye de forma PURA con una o mas
 * {@link MotorCosteo#aplicarEntrada}; luego se ejecutan las dos patas de la
 * transferencia (salida del origen + entrada al destino). Cubre las cuatro
 * combinaciones de metodo origen/destino ({@link MetodoCosteo#PROMEDIO} y
 * {@link MetodoCosteo#PEPS}). Las comparaciones usan {@code compareTo} para ignorar
 * la representacion de escala.</p>
 */
class TransferenciaConservaCantidadCostoPropertyTest {

    private static final int ESCALA_CANTIDAD = MotorCosteo.ESCALA_CANTIDAD; // 3
    private static final int ESCALA_COSTO = MotorCosteo.ESCALA_COSTO; // 4

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Cantidad estrictamente positiva a escala 3, en milesimas de 1 a 10_000_000 (0.001..10000). */
    private static Arbitrary<BigDecimal> cantidadPositiva() {
        return Arbitraries.longs()
                .between(1L, 10_000_000L)
                .map(milesimas -> new BigDecimal(milesimas).movePointLeft(ESCALA_CANTIDAD));
    }

    /** Costo unitario &gt;= 0 a escala 4, en diezmilesimas de 0 a 100_000_000 (0..10000). */
    private static Arbitrary<BigDecimal> costoNoNegativo() {
        return Arbitraries.longs()
                .between(0L, 100_000_000L)
                .map(diezmilesimas -> new BigDecimal(diezmilesimas).movePointLeft(ESCALA_COSTO));
    }

    /** Metodo de costeo del almacen origen. */
    @Provide
    Arbitrary<MetodoCosteo> metodosOrigen() {
        return Arbitraries.of(MetodoCosteo.class);
    }

    /** Metodo de costeo del almacen destino. */
    @Provide
    Arbitrary<MetodoCosteo> metodosDestino() {
        return Arbitraries.of(MetodoCosteo.class);
    }

    /** Una lista de 1..5 entradas (cantidad, costo) para sembrar el estado del origen. */
    @Provide
    Arbitrary<List<Entrada>> siembras() {
        Arbitrary<Entrada> entrada = net.jqwik.api.Combinators
                .combine(cantidadPositiva(), costoNoNegativo())
                .as(Entrada::new);
        return entrada.list().ofMinSize(1).ofMaxSize(5);
    }

    /** Fraccion positiva usada para derivar una cantidad de transferencia <= saldo del origen. */
    @Provide
    Arbitrary<BigDecimal> fracciones() {
        return cantidadPositiva();
    }

    // ----------------------------------------------------------------------
    // Property 32 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 32: Transferencia entre Almacenes conserva cantidad y costo
    @Property(tries = 1000)
    void transferenciaConservaCantidadYCostoEntreAlmacenes(
            @ForAll("metodosOrigen") MetodoCosteo metodoOrigen,
            @ForAll("metodosDestino") MetodoCosteo metodoDestino,
            @ForAll("siembras") List<Entrada> siembras,
            @ForAll("fracciones") BigDecimal fraccion,
            @ForAll("metodosDestino") MetodoCosteo destinoSemilla,
            @ForAll("fracciones") BigDecimal saldoDestinoSemilla) {

        // --- Construir el estado del origen de forma pura via aplicarEntrada. ---
        BigDecimal saldoOrigen = BigDecimal.ZERO;
        BigDecimal promedioOrigen = BigDecimal.ZERO;
        List<CapaCostoValor> capasOrigen = List.of();
        for (Entrada e : siembras) {
            ResultadoEntrada r = MotorCosteo.aplicarEntrada(
                    metodoOrigen, saldoOrigen, promedioOrigen, capasOrigen, e.cantidad, e.costo);
            saldoOrigen = r.nuevoSaldoCantidad();
            promedioOrigen = r.nuevoCostoPromedio();
            capasOrigen = r.nuevasCapas();
        }

        // Cantidad de transferencia valida: 0 < cantidad <= saldo del origen.
        BigDecimal cantidad = fraccion.min(saldoOrigen).setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);

        // --- Pata 1: SALIDA del origen (obtiene el costo unitario del movimiento). ---
        ResultadoSalida salida = MotorCosteo.aplicarSalida(
                metodoOrigen, saldoOrigen, promedioOrigen, capasOrigen, cantidad);
        BigDecimal cu = salida.costoUnitarioMovimiento();

        BigDecimal esperadoSaldoOrigen = saldoOrigen.subtract(cantidad)
                .setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
        assertThat(salida.nuevoSaldoCantidad())
                .as("el saldo del origen disminuye en la cantidad transferida (%s -> %s)",
                        saldoOrigen, esperadoSaldoOrigen)
                .isEqualByComparingTo(esperadoSaldoOrigen);

        // --- Sembrar un saldo previo arbitrario en el destino (para no partir de vacio). ---
        BigDecimal saldoDestino = BigDecimal.ZERO;
        BigDecimal promedioDestino = BigDecimal.ZERO;
        List<CapaCostoValor> capasDestino = List.of();
        ResultadoEntrada semillaDestino = MotorCosteo.aplicarEntrada(
                destinoSemilla, saldoDestino, promedioDestino, capasDestino,
                saldoDestinoSemilla, cu);
        saldoDestino = semillaDestino.nuevoSaldoCantidad();
        promedioDestino = semillaDestino.nuevoCostoPromedio();
        capasDestino = semillaDestino.nuevasCapas();

        BigDecimal saldoDestinoAntes = saldoDestino;

        // --- Pata 2: ENTRADA al destino con el MISMO costo unitario y la MISMA cantidad. ---
        ResultadoEntrada entradaDestino = MotorCosteo.aplicarEntrada(
                metodoDestino, saldoDestino, promedioDestino, capasDestino, cantidad, cu);

        // (1) Conservacion de cantidad: el destino aumenta exactamente en la cantidad transferida.
        BigDecimal esperadoSaldoDestino = saldoDestinoAntes.add(cantidad)
                .setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
        assertThat(entradaDestino.nuevoSaldoCantidad())
                .as("el destino aumenta en la MISMA cantidad que dejo el origen (%s + %s)",
                        saldoDestinoAntes, cantidad)
                .isEqualByComparingTo(esperadoSaldoDestino);

        // (2) Conservacion de costo: el motor refleja el costo unitario transferido tal cual.
        assertThat(entradaDestino.costoUnitarioMovimiento())
                .as("la entrada al destino conserva el costo unitario del movimiento del origen (%s)", cu)
                .isEqualByComparingTo(cu.setScale(ESCALA_COSTO, RoundingMode.HALF_UP));

        // (3) Costo total conservado: costoTotal destino == cu * cantidad (escala 4).
        BigDecimal esperadoCostoTotal = cantidad.multiply(cu)
                .setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
        assertThat(entradaDestino.costoTotalMovimiento())
                .as("el costo total de la entrada destino == cu(%s) * cantidad(%s)", cu, cantidad)
                .isEqualByComparingTo(esperadoCostoTotal);
    }

    /** Modelo en memoria de una entrada de siembra (cantidad, costo unitario). */
    private static final class Entrada {
        private final BigDecimal cantidad;
        private final BigDecimal costo;

        private Entrada(BigDecimal cantidad, BigDecimal costo) {
            this.cantidad = cantidad;
            this.costo = costo;
        }
    }
}
