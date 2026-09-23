package com.dessti.crm.operacion.inventario.avanzado.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 33: Recosteo
 * correcto segun metodo (promedio/PEPS)</strong> (Req 60.5, 60.11).
 *
 * <p>Ejercita el motor de costeo PURO {@link MotorCosteo} contra un modelo de
 * referencia calculado de forma independiente en el propio test:</p>
 *
 * <ul>
 *   <li><strong>PROMEDIO — entrada:</strong> tras una entrada el nuevo costo
 *       promedio es exactamente
 *       {@code round((saldo*promedio + cantidad*costo) / (saldo+cantidad), 4, HALF_UP)}.</li>
 *   <li><strong>PROMEDIO — salida:</strong> una salida NO cambia el promedio y su
 *       {@code costoUnitarioMovimiento} es igual al promedio vigente (escala 4).</li>
 *   <li><strong>PEPS — salida:</strong> la salida consume las capas mas ANTIGUAS
 *       primero; el {@code costoTotalMovimiento} coincide con la suma exacta de las
 *       porciones consumidas por capa (redondeada solo al final), y las capas
 *       restantes coinciden con el consumo desde el frente (tamano y primera capa
 *       reducida).</li>
 * </ul>
 *
 * <p>Para las aserciones de PEPS se usan costos decimales fijos y cantidades
 * enteras, de modo que la suma exacta sea reproducible sin ambiguedad de
 * redondeo. Todas las comparaciones de {@link BigDecimal} usan {@code compareTo}.</p>
 */
class RecosteoSegunMetodoPropertyTest {

    private static final int ESCALA_CANTIDAD = MotorCosteo.ESCALA_CANTIDAD; // 3
    private static final int ESCALA_COSTO = MotorCosteo.ESCALA_COSTO; // 4

    /** Costos fijos con escala 4 exacta para el escenario PEPS. */
    private static final BigDecimal[] COSTOS_PEPS = {
            new BigDecimal("5.0000"),
            new BigDecimal("7.5000"),
            new BigDecimal("10.0000")
    };

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Saldo &gt;= 0 a escala 3 (0..100000). */
    private static Arbitrary<BigDecimal> saldoNoNegativo() {
        return Arbitraries.longs()
                .between(0L, 100_000_000L)
                .map(m -> new BigDecimal(m).movePointLeft(ESCALA_CANTIDAD));
    }

    /** Cantidad estrictamente positiva a escala 3 (0.001..100000). */
    private static Arbitrary<BigDecimal> cantidadPositiva() {
        return Arbitraries.longs()
                .between(1L, 100_000_000L)
                .map(m -> new BigDecimal(m).movePointLeft(ESCALA_CANTIDAD));
    }

    /** Costo unitario &gt;= 0 a escala 4 (0..100000). */
    private static Arbitrary<BigDecimal> costoNoNegativo() {
        return Arbitraries.longs()
                .between(0L, 1_000_000_000L)
                .map(m -> new BigDecimal(m).movePointLeft(ESCALA_COSTO));
    }

    @Provide
    Arbitrary<BigDecimal> saldos() {
        return saldoNoNegativo();
    }

    @Provide
    Arbitrary<BigDecimal> promedios() {
        return costoNoNegativo();
    }

    @Provide
    Arbitrary<BigDecimal> cantidades() {
        return cantidadPositiva();
    }

    @Provide
    Arbitrary<BigDecimal> costos() {
        return costoNoNegativo();
    }

    /** Una capa PEPS: cantidad entera 1..20 y costo unitario de un conjunto fijo. */
    @Provide
    Arbitrary<CapaSemilla> capasSemilla() {
        Arbitrary<BigDecimal> cantidades = Arbitraries.integers().between(1, 20)
                .map(n -> new BigDecimal(n).setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP));
        Arbitrary<BigDecimal> costos = Arbitraries.of(COSTOS_PEPS);
        return Combinators.combine(cantidades, costos).as(CapaSemilla::new);
    }

    /** Secuencia de 1..6 capas PEPS con costos distintos entre si por posicion. */
    @Provide
    Arbitrary<List<CapaSemilla>> secuenciasDeCapas() {
        return capasSemilla().list().ofMinSize(1).ofMaxSize(6);
    }

    // ----------------------------------------------------------------------
    // Property 33 — PROMEDIO
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 33: Recosteo correcto según método (promedio/PEPS)
    @Property(tries = 1000)
    void promedioEntradaRecalculaMediaPonderada(
            @ForAll("saldos") BigDecimal saldo,
            @ForAll("promedios") BigDecimal promedioActual,
            @ForAll("cantidades") BigDecimal cantidad,
            @ForAll("costos") BigDecimal costo) {

        ResultadoEntrada resultado = MotorCosteo.aplicarEntrada(
                MetodoCosteo.PROMEDIO, saldo, promedioActual, List.of(), cantidad, costo);

        BigDecimal nuevoSaldo = saldo.add(cantidad);
        BigDecimal esperado;
        if (nuevoSaldo.signum() == 0) {
            esperado = BigDecimal.ZERO.setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
        } else {
            BigDecimal valorPrevio = saldo.multiply(promedioActual);
            BigDecimal valorEntrada = cantidad.multiply(costo);
            esperado = valorPrevio.add(valorEntrada)
                    .divide(nuevoSaldo, ESCALA_COSTO, RoundingMode.HALF_UP);
        }

        assertThat(resultado.nuevoCostoPromedio())
                .as("promedio == round((saldo*prom + cant*costo)/(saldo+cant), 4)")
                .isEqualByComparingTo(esperado);
        assertThat(resultado.nuevoCostoPromedio().scale())
                .as("el promedio debe tener escala 4")
                .isEqualTo(ESCALA_COSTO);
    }

    // Feature: crm-anuncios-luminosos, Property 33: Recosteo correcto según método (promedio/PEPS)
    @Property(tries = 1000)
    void promedioSalidaNoCambiaPromedioYCostoUnitarioEsElPromedio(
            @ForAll("promedios") BigDecimal promedioActual,
            @ForAll("cantidades") BigDecimal base,
            @ForAll("cantidades") BigDecimal fraccion) {

        // Sembrar un saldo con existencias reales; el promedio se fuerza al valor generado
        // usando una entrada cuyo costo es el promedio deseado.
        ResultadoEntrada estado = MotorCosteo.aplicarEntrada(
                MetodoCosteo.PROMEDIO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), base, promedioActual);
        BigDecimal saldo = estado.nuevoSaldoCantidad();
        BigDecimal promedio = estado.nuevoCostoPromedio();

        BigDecimal cantidadSalida = fraccion.min(saldo).setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);

        ResultadoSalida resultado = MotorCosteo.aplicarSalida(
                MetodoCosteo.PROMEDIO, saldo, promedio, estado.nuevasCapas(), cantidadSalida);

        assertThat(resultado.nuevoCostoPromedio())
                .as("una salida en PROMEDIO no cambia el promedio vigente (%s)", promedio)
                .isEqualByComparingTo(promedio);
        assertThat(resultado.costoUnitarioMovimiento())
                .as("el costo unitario de una salida PROMEDIO es el promedio vigente (%s)", promedio)
                .isEqualByComparingTo(promedio.setScale(ESCALA_COSTO, RoundingMode.HALF_UP));

        BigDecimal esperadoTotal = cantidadSalida.multiply(promedio)
                .setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
        assertThat(resultado.costoTotalMovimiento())
                .as("el costo total de una salida PROMEDIO == cantidad * promedio")
                .isEqualByComparingTo(esperadoTotal);
    }

    // ----------------------------------------------------------------------
    // Property 33 — PEPS
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 33: Recosteo correcto según método (promedio/PEPS)
    @Property(tries = 500)
    void pepsSalidaConsumeCapasMasAntiguasPrimero(
            @ForAll("secuenciasDeCapas") List<CapaSemilla> capasSemilla,
            @ForAll("cantidades") BigDecimal fraccion) {

        // Construir el estado PEPS de forma pura: entradas en orden (mas antigua primero).
        BigDecimal saldo = BigDecimal.ZERO;
        BigDecimal promedio = BigDecimal.ZERO;
        List<CapaCostoValor> capas = List.of();
        for (CapaSemilla cs : capasSemilla) {
            ResultadoEntrada r = MotorCosteo.aplicarEntrada(
                    MetodoCosteo.PEPS, saldo, promedio, capas, cs.cantidad, cs.costo);
            saldo = r.nuevoSaldoCantidad();
            promedio = r.nuevoCostoPromedio();
            capas = r.nuevasCapas();
        }

        // Cantidad de salida valida: 0 < cantidad <= saldo total.
        BigDecimal cantidadSalida = fraccion.min(saldo).setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);

        // Modelo de referencia: consumo desde el frente (capas mas antiguas primero).
        List<CapaCostoValor> capasRestantesEsperadas = new ArrayList<>();
        BigDecimal porConsumir = cantidadSalida;
        BigDecimal costoTotalExacto = BigDecimal.ZERO;
        for (CapaCostoValor capa : capas) {
            BigDecimal disponible = capa.cantidadRestante();
            if (porConsumir.signum() <= 0) {
                capasRestantesEsperadas.add(capa);
                continue;
            }
            if (disponible.compareTo(porConsumir) <= 0) {
                costoTotalExacto = costoTotalExacto.add(disponible.multiply(capa.costoUnitario()));
                porConsumir = porConsumir.subtract(disponible);
            } else {
                costoTotalExacto = costoTotalExacto.add(porConsumir.multiply(capa.costoUnitario()));
                BigDecimal remanente = disponible.subtract(porConsumir)
                        .setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
                capasRestantesEsperadas.add(new CapaCostoValor(remanente, capa.costoUnitario()));
                porConsumir = BigDecimal.ZERO;
            }
        }
        BigDecimal costoTotalEsperado = costoTotalExacto.setScale(ESCALA_COSTO, RoundingMode.HALF_UP);

        ResultadoSalida resultado = MotorCosteo.aplicarSalida(
                MetodoCosteo.PEPS, saldo, promedio, capas, cantidadSalida);

        // (a) Costo total == suma exacta de porciones consumidas (redondeada al final).
        assertThat(resultado.costoTotalMovimiento())
                .as("PEPS: costo total == suma exacta de consumos por capa desde el frente")
                .isEqualByComparingTo(costoTotalEsperado);

        // (b) Costo unitario del movimiento == costoTotal / cantidad (escala 4).
        BigDecimal cuEsperado = costoTotalEsperado.divide(cantidadSalida, ESCALA_COSTO, RoundingMode.HALF_UP);
        assertThat(resultado.costoUnitarioMovimiento())
                .as("PEPS: costo unitario del movimiento == costoTotal / cantidad")
                .isEqualByComparingTo(cuEsperado);

        // (c) Las capas restantes coinciden con el consumo desde el frente (tamano y contenido).
        assertThat(resultado.nuevasCapas())
                .as("PEPS: numero de capas restantes tras consumo FIFO")
                .hasSize(capasRestantesEsperadas.size());
        for (int i = 0; i < capasRestantesEsperadas.size(); i++) {
            CapaCostoValor real = resultado.nuevasCapas().get(i);
            CapaCostoValor esperada = capasRestantesEsperadas.get(i);
            assertThat(real.cantidadRestante())
                    .as("PEPS: cantidad restante de la capa %d tras el consumo desde el frente", i)
                    .isEqualByComparingTo(esperada.cantidadRestante());
            assertThat(real.costoUnitario())
                    .as("PEPS: costo unitario de la capa %d se conserva", i)
                    .isEqualByComparingTo(esperada.costoUnitario());
        }
    }

    /** Semilla de una capa PEPS: cantidad entera y costo unitario fijo con escala 4. */
    private static final class CapaSemilla {
        private final BigDecimal cantidad;
        private final BigDecimal costo;

        private CapaSemilla(BigDecimal cantidad, BigDecimal costo) {
            this.cantidad = cantidad;
            this.costo = costo;
        }
    }
}
