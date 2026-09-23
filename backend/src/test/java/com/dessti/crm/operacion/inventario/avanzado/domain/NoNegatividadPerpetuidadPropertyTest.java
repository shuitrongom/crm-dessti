package com.dessti.crm.operacion.inventario.avanzado.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 31: No
 * negatividad y perpetuidad de existencias por Almacen</strong> (Req 60.10,
 * 60.11; coherente con Req 18.3 del inventario base).
 *
 * <p>Ejercita directamente el motor de costeo PURO
 * {@link MotorCosteo#aplicarEntrada(MetodoCosteo, BigDecimal, BigDecimal, List, BigDecimal, BigDecimal)}
 * y {@link MotorCosteo#aplicarSalida(MetodoCosteo, BigDecimal, BigDecimal, List, BigDecimal)},
 * sin base de datos ni contexto de Spring: los metodos del motor son estaticos,
 * sin estado y deterministas, por lo que las invariantes de no negatividad y
 * perpetuidad se comprueban universalmente sobre entradas arbitrarias, para
 * <em>ambos</em> metodos de costeo ({@link MetodoCosteo#PROMEDIO} y
 * {@link MetodoCosteo#PEPS}).</p>
 *
 * <h2>Invariantes verificados (Property 31)</h2>
 * <ol>
 *   <li><strong>Entrada:</strong> con cantidad &gt; 0 y costo &gt;= 0, el nuevo
 *       saldo es exactamente {@code saldoInicial + cantidad} (escala 3) y nunca
 *       es negativo.</li>
 *   <li><strong>Salida valida:</strong> con {@code 0 < cantidad <= saldo}, el
 *       nuevo saldo es exactamente {@code saldo - cantidad} (escala 3) y nunca es
 *       negativo.</li>
 *   <li><strong>Perpetuidad:</strong> una salida con {@code cantidad > saldo} se
 *       rechaza con {@link ReglaNegocioException} y mensaje
 *       "existencias insuficientes" ANTES de mutar nada (el saldo se conservaria).</li>
 *   <li><strong>Validacion de dominio:</strong> una entrada con cantidad &lt;= 0
 *       o costo &lt; 0, y una salida con cantidad &lt;= 0, se rechazan con
 *       {@link ReglaNegocioException}.</li>
 * </ol>
 *
 * <h2>Convenciones numericas (espejo de produccion)</h2>
 * <p>Cantidades a escala {@value MotorCosteo#ESCALA_CANTIDAD} y costos a escala
 * {@value MotorCosteo#ESCALA_COSTO}, ambos con redondeo {@code HALF_UP},
 * coherentes con {@code NUMERIC(18,3)}/{@code NUMERIC(18,4)} de V26. Los
 * generadores construyen cantidades y costos a partir de enteros escalados para
 * mantener comparaciones exactas por {@code compareTo}.</p>
 */
class NoNegatividadPerpetuidadPropertyTest {

    /** Escala de cantidades espejo de produccion. */
    private static final int ESCALA_CANTIDAD = MotorCosteo.ESCALA_CANTIDAD; // 3

    /** Escala de costos espejo de produccion. */
    private static final int ESCALA_COSTO = MotorCosteo.ESCALA_COSTO; // 4

    /** Cero escalado a la escala de cantidades para comparaciones exactas. */
    private static final BigDecimal CERO_CANTIDAD =
            BigDecimal.ZERO.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Cantidad estrictamente positiva a escala 3, en milesimas de 1 a 100_000_000 (0.001..100000). */
    private static Arbitrary<BigDecimal> cantidadPositiva() {
        return Arbitraries.longs()
                .between(1L, 100_000_000L)
                .map(milesimas -> new BigDecimal(milesimas).movePointLeft(ESCALA_CANTIDAD));
    }

    /** Saldo &gt;= 0 a escala 3, en milesimas de 0 a 100_000_000 (0..100000). */
    private static Arbitrary<BigDecimal> saldoNoNegativo() {
        return Arbitraries.longs()
                .between(0L, 100_000_000L)
                .map(milesimas -> new BigDecimal(milesimas).movePointLeft(ESCALA_CANTIDAD));
    }

    /** Costo unitario &gt;= 0 a escala 4, en diezmilesimas de 0 a 1_000_000_000 (0..100000). */
    private static Arbitrary<BigDecimal> costoNoNegativo() {
        return Arbitraries.longs()
                .between(0L, 1_000_000_000L)
                .map(diezmilesimas -> new BigDecimal(diezmilesimas).movePointLeft(ESCALA_COSTO));
    }

    /** Ambos metodos de costeo, para ejercitar la invariante en PROMEDIO y PEPS. */
    @Provide
    Arbitrary<MetodoCosteo> metodos() {
        return Arbitraries.of(MetodoCosteo.class);
    }

    /** Cantidad positiva expuesta como generador con nombre. */
    @Provide
    Arbitrary<BigDecimal> cantidadesPositivas() {
        return cantidadPositiva();
    }

    /** Saldo no negativo expuesto como generador con nombre. */
    @Provide
    Arbitrary<BigDecimal> saldos() {
        return saldoNoNegativo();
    }

    /** Costo no negativo expuesto como generador con nombre. */
    @Provide
    Arbitrary<BigDecimal> costos() {
        return costoNoNegativo();
    }

    // ----------------------------------------------------------------------
    // Property 31 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 31: No negatividad y perpetuidad de existencias por Almacén
    @Property(tries = 1000)
    void entradaSumaExactamenteLaCantidadYNoEsNegativa(
            @ForAll("metodos") MetodoCosteo metodo,
            @ForAll("saldos") BigDecimal saldoInicial,
            @ForAll("costos") BigDecimal costoPromedioActual,
            @ForAll("cantidadesPositivas") BigDecimal cantidadEntrada,
            @ForAll("costos") BigDecimal costoUnitarioEntrada) {

        ResultadoEntrada resultado = MotorCosteo.aplicarEntrada(
                metodo, saldoInicial, costoPromedioActual, List.of(),
                cantidadEntrada, costoUnitarioEntrada);

        BigDecimal esperado = saldoInicial.add(cantidadEntrada)
                .setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);

        assertThat(resultado.nuevoSaldoCantidad())
                .as("nuevo saldo tras entrada == saldoInicial(%s) + cantidad(%s) [%s]",
                        saldoInicial, cantidadEntrada, metodo)
                .isEqualByComparingTo(esperado);
        assertThat(resultado.nuevoSaldoCantidad())
                .as("el saldo tras una entrada nunca es negativo")
                .isGreaterThanOrEqualTo(CERO_CANTIDAD);
        assertThat(resultado.nuevoSaldoCantidad().scale())
                .as("el saldo tras una entrada debe tener escala 3")
                .isEqualTo(ESCALA_CANTIDAD);
    }

    // Feature: crm-anuncios-luminosos, Property 31: No negatividad y perpetuidad de existencias por Almacén
    @Property(tries = 1000)
    void salidaValidaRestaExactamenteLaCantidadYNoEsNegativa(
            @ForAll("metodos") MetodoCosteo metodo,
            @ForAll("saldos") BigDecimal saldoSemilla,
            @ForAll("costos") BigDecimal costoUnitario,
            @ForAll("cantidadesPositivas") BigDecimal fraccion) {

        // Sembrar un saldo con existencias reales via una entrada (base >= cantidad de salida).
        BigDecimal base = saldoSemilla.add(fraccion);
        ResultadoEntrada estado = MotorCosteo.aplicarEntrada(
                metodo, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), base, costoUnitario);
        BigDecimal saldo = estado.nuevoSaldoCantidad();

        // Salida valida: 0 < cantidad <= saldo (usamos la fraccion sembrada, acotada al saldo).
        BigDecimal cantidadSalida = fraccion.min(saldo).setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);

        ResultadoSalida resultado = MotorCosteo.aplicarSalida(
                metodo, saldo, estado.nuevoCostoPromedio(), estado.nuevasCapas(), cantidadSalida);

        BigDecimal esperado = saldo.subtract(cantidadSalida)
                .setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);

        assertThat(resultado.nuevoSaldoCantidad())
                .as("nuevo saldo tras salida == saldo(%s) - cantidad(%s) [%s]",
                        saldo, cantidadSalida, metodo)
                .isEqualByComparingTo(esperado);
        assertThat(resultado.nuevoSaldoCantidad())
                .as("el saldo tras una salida valida nunca es negativo")
                .isGreaterThanOrEqualTo(CERO_CANTIDAD);
        assertThat(resultado.nuevoSaldoCantidad().scale())
                .as("el saldo tras una salida debe tener escala 3")
                .isEqualTo(ESCALA_CANTIDAD);
    }

    // Feature: crm-anuncios-luminosos, Property 31: No negatividad y perpetuidad de existencias por Almacén
    @Property(tries = 1000)
    void salidaQueExcedeElSaldoSeRechazaYConservaLaPerpetuidad(
            @ForAll("metodos") MetodoCosteo metodo,
            @ForAll("saldos") BigDecimal saldo,
            @ForAll("costos") BigDecimal costoPromedio,
            @ForAll("cantidadesPositivas") BigDecimal delta) {

        // Una salida estrictamente mayor que el saldo: cantidad = saldo + delta (delta > 0).
        BigDecimal cantidadExceso = saldo.add(delta).setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);

        assertThatThrownBy(() -> MotorCosteo.aplicarSalida(
                metodo, saldo, costoPromedio, List.of(), cantidadExceso))
                .as("una salida de %s sobre un saldo de %s debe rechazarse por insuficiencia [%s]",
                        cantidadExceso, saldo, metodo)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("existencias insuficientes");
    }

    // Feature: crm-anuncios-luminosos, Property 31: No negatividad y perpetuidad de existencias por Almacén
    @Property(tries = 1000)
    void entradaConCantidadNoPositivaOCostoNegativoSeRechaza(
            @ForAll("metodos") MetodoCosteo metodo,
            @ForAll("saldos") BigDecimal saldo,
            @ForAll("costos") BigDecimal costoPromedio,
            @ForAll("cantidadesPositivas") BigDecimal magnitud) {

        // Cantidad no positiva (0 y negativa) debe rechazarse.
        assertThatThrownBy(() -> MotorCosteo.aplicarEntrada(
                metodo, saldo, costoPromedio, List.of(), BigDecimal.ZERO, magnitud))
                .as("una entrada con cantidad 0 debe rechazarse [%s]", metodo)
                .isInstanceOf(ReglaNegocioException.class);

        assertThatThrownBy(() -> MotorCosteo.aplicarEntrada(
                metodo, saldo, costoPromedio, List.of(), magnitud.negate(), magnitud))
                .as("una entrada con cantidad negativa debe rechazarse [%s]", metodo)
                .isInstanceOf(ReglaNegocioException.class);

        // Costo negativo debe rechazarse.
        assertThatThrownBy(() -> MotorCosteo.aplicarEntrada(
                metodo, saldo, costoPromedio, List.of(), magnitud, magnitud.negate()))
                .as("una entrada con costo negativo debe rechazarse [%s]", metodo)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 31: No negatividad y perpetuidad de existencias por Almacén
    @Property(tries = 1000)
    void salidaConCantidadNoPositivaSeRechaza(
            @ForAll("metodos") MetodoCosteo metodo,
            @ForAll("saldos") BigDecimal saldo,
            @ForAll("costos") BigDecimal costoPromedio,
            @ForAll("cantidadesPositivas") BigDecimal magnitud) {

        assertThatThrownBy(() -> MotorCosteo.aplicarSalida(
                metodo, saldo, costoPromedio, List.of(), BigDecimal.ZERO))
                .as("una salida con cantidad 0 debe rechazarse [%s]", metodo)
                .isInstanceOf(ReglaNegocioException.class);

        assertThatThrownBy(() -> MotorCosteo.aplicarSalida(
                metodo, saldo, costoPromedio, List.of(), magnitud.negate()))
                .as("una salida con cantidad negativa debe rechazarse [%s]", metodo)
                .isInstanceOf(ReglaNegocioException.class);
    }
}
