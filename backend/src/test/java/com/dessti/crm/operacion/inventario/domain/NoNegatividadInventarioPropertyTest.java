package com.dessti.crm.operacion.inventario.domain;

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
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 9: No
 * negatividad de existencias de inventario</strong> (Req 18.2, 18.3, 18.4).
 *
 * <p>Reutiliza la raiz de agregado de produccion {@link Material} como pieza
 * pura de dominio: se instancia con la factory
 * {@link Material#crear(String, String, BigDecimal, String)} (existencias
 * iniciales 0, Req 18.1) y se conduce mediante
 * {@link Material#aplicarMovimiento(TipoMovimientoInventario, BigDecimal, String)},
 * sin base de datos ni contexto de Spring. La property comprueba
 * universalmente, sobre secuencias arbitrarias de {@link MovimientoInventario},
 * la invariante de no negatividad y la conservacion del saldo:</p>
 *
 * <ol>
 *   <li><strong>No negatividad:</strong> tras cada movimiento aceptado, las
 *       existencias son &gt;= 0.</li>
 *   <li><strong>Rechazo con conservacion:</strong> una salida (o un ajuste
 *       negativo) que dejaria las existencias por debajo de 0 se rechaza con
 *       {@link ReglaNegocioException} y las existencias se conservan sin
 *       cambios (valor antes == valor despues).</li>
 *   <li><strong>Delta exacto con signo:</strong> una entrada suma exactamente
 *       la cantidad, una salida resta exactamente la cantidad y un ajuste
 *       aplica exactamente el delta con signo (escala 3, HALF_UP).</li>
 *   <li><strong>Consistencia del saldo:</strong> a lo largo de la secuencia, el
 *       saldo que el test lleva en un modelo de referencia coincide siempre con
 *       {@link Material#getExistencias()} y nunca es negativo.</li>
 *   <li><strong>Indicador de stock bajo:</strong> el {@code stockBajo} devuelto
 *       equivale a {@code existencias < stockMinimo} tras el movimiento
 *       (Req 18.5).</li>
 * </ol>
 *
 * <h2>Semantica espejo de produccion</h2>
 * <p>Las cantidades se manejan a escala 3 (HALF_UP), coherente con
 * {@link Material#ESCALA_CANTIDAD} y {@code NUMERIC(18,3)} de V18. Los
 * generadores producen cantidades <em>validas por tipo</em> (entrada/salida
 * estrictamente positivas; ajuste distinto de cero) para ejercitar la logica de
 * no negatividad —no la validacion de cantidad—, replicando el delta con signo
 * de {@code calcularDelta} para decidir, en cada paso, si el movimiento debe
 * aceptarse o rechazarse.</p>
 */
class NoNegatividadInventarioPropertyTest {

    /** Escala de cantidades espejo de produccion. */
    private static final int ESCALA = Material.ESCALA_CANTIDAD; // 3

    /** Actor arbitrario para las marcas de auditoria; la factory solo exige no nulo. */
    private static final String ACTOR = "tester-inventario";

    /** Cero escalado a la escala de cantidades, para comparaciones exactas. */
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);

    // ----------------------------------------------------------------------
    // Modelo de un movimiento generado (tipo + cantidad valida para ese tipo)
    // ----------------------------------------------------------------------

    /** Movimiento generado: tipo y cantidad ya valida para ese tipo. */
    private record MovimientoGenerado(TipoMovimientoInventario tipo, BigDecimal cantidad) {

        /** Delta con signo que este movimiento aplicaria sobre las existencias. */
        BigDecimal deltaConSigno() {
            return switch (tipo) {
                case ENTRADA -> cantidad;
                case SALIDA -> cantidad.negate();
                case AJUSTE -> cantidad;
            };
        }
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Cantidad estrictamente positiva a escala 3, en milesimas de 1 a 1_000_000. */
    private static Arbitrary<BigDecimal> cantidadPositiva() {
        return Arbitraries.longs()
                .between(1L, 1_000_000L)
                .map(milesimas -> new BigDecimal(milesimas).movePointLeft(ESCALA));
    }

    /** Cantidad con signo distinta de cero a escala 3 (para ajustes). */
    private static Arbitrary<BigDecimal> cantidadNoCero() {
        return Arbitraries.longs()
                .between(-1_000_000L, 1_000_000L)
                .filter(milesimas -> milesimas != 0L)
                .map(milesimas -> new BigDecimal(milesimas).movePointLeft(ESCALA));
    }

    /** Stock minimo &gt;= 0 a escala 3, en milesimas de 0 a 500_000. */
    @Provide
    Arbitrary<BigDecimal> stockMinimos() {
        return Arbitraries.longs()
                .between(0L, 500_000L)
                .map(milesimas -> new BigDecimal(milesimas).movePointLeft(ESCALA));
    }

    /** Un movimiento generado con cantidad valida segun su tipo. */
    private Arbitrary<MovimientoGenerado> movimiento() {
        Arbitrary<MovimientoGenerado> entrada = cantidadPositiva()
                .map(c -> new MovimientoGenerado(TipoMovimientoInventario.ENTRADA, c));
        Arbitrary<MovimientoGenerado> salida = cantidadPositiva()
                .map(c -> new MovimientoGenerado(TipoMovimientoInventario.SALIDA, c));
        Arbitrary<MovimientoGenerado> ajuste = cantidadNoCero()
                .map(c -> new MovimientoGenerado(TipoMovimientoInventario.AJUSTE, c));
        // Sesgo hacia entradas/salidas, con ajustes menos frecuentes.
        return Arbitraries.oneOf(entrada, entrada, salida, salida, ajuste);
    }

    /** Secuencia de 1..20 movimientos generados. */
    @Provide
    Arbitrary<List<MovimientoGenerado>> secuenciasDeMovimientos() {
        return movimiento().list().ofMinSize(1).ofMaxSize(20);
    }

    /** Una unica entrada positiva, para sembrar un stock base arbitrario. */
    @Provide
    Arbitrary<BigDecimal> stockBase() {
        return cantidadPositiva();
    }

    // ----------------------------------------------------------------------
    // Property 9 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 9: Para cualquier secuencia de Movimiento_Inventario sobre un Material, las existencias resultantes equivalen a la suma con signo de los movimientos aplicados y nunca son negativas; una salida que dejaria las existencias por debajo de 0 se rechaza y conserva las existencias sin cambios.
    @Property(tries = 1000)
    void secuenciaDeMovimientosMantieneNoNegatividadYSaldoConsistente(
            @ForAll("stockMinimos") BigDecimal stockMinimo,
            @ForAll("secuenciasDeMovimientos") List<MovimientoGenerado> movimientos) {

        Material material = Material.crear("Vinil", "metro", stockMinimo, ACTOR);
        assertThat(material.getExistencias())
                .as("las existencias iniciales de un Material recien creado deben ser 0")
                .isEqualByComparingTo(CERO);

        // Modelo de referencia: el saldo esperado que el test lleva por su cuenta.
        BigDecimal saldoEsperado = CERO;

        for (MovimientoGenerado mov : movimientos) {
            BigDecimal existenciasAntes = material.getExistencias();
            BigDecimal esperadoTrasMov = saldoEsperado.add(mov.deltaConSigno())
                    .setScale(ESCALA, RoundingMode.HALF_UP);

            if (esperadoTrasMov.signum() < 0) {
                // (2) Debe rechazarse y conservar las existencias sin cambios.
                assertThatThrownBy(() ->
                        material.aplicarMovimiento(mov.tipo(), mov.cantidad(), ACTOR))
                        .as("un %s de %s que dejaria el saldo en %s debe rechazarse",
                                mov.tipo(), mov.cantidad(), esperadoTrasMov)
                        .isInstanceOf(ReglaNegocioException.class);
                assertThat(material.getExistencias())
                        .as("tras un movimiento rechazado las existencias se conservan sin cambios")
                        .isEqualByComparingTo(existenciasAntes);
                // El modelo de referencia tampoco cambia.
            } else {
                // (3) Debe aceptarse con el delta exacto con signo.
                ResultadoMovimiento resultado =
                        material.aplicarMovimiento(mov.tipo(), mov.cantidad(), ACTOR);

                assertThat(resultado.existenciasResultantes())
                        .as("existencias tras %s de %s: antes(%s) + delta(%s)",
                                mov.tipo(), mov.cantidad(), existenciasAntes, mov.deltaConSigno())
                        .isEqualByComparingTo(esperadoTrasMov);
                // (1) Nunca negativas tras un movimiento aceptado.
                assertThat(resultado.existenciasResultantes())
                        .as("las existencias nunca deben ser negativas")
                        .isGreaterThanOrEqualTo(CERO);
                // (5) El indicador de stock bajo equivale a existencias < stockMinimo.
                assertThat(resultado.stockBajo())
                        .as("stockBajo debe ser (existencias %s < stockMinimo %s)",
                                esperadoTrasMov, stockMinimo)
                        .isEqualTo(esperadoTrasMov.compareTo(stockMinimo) < 0);

                saldoEsperado = esperadoTrasMov;
            }

            // (4) Consistencia: el saldo del material coincide con el modelo y es >= 0.
            assertThat(material.getExistencias())
                    .as("el saldo del Material debe coincidir con el modelo de referencia")
                    .isEqualByComparingTo(saldoEsperado);
            assertThat(material.getExistencias())
                    .as("el saldo del Material nunca debe ser negativo")
                    .isGreaterThanOrEqualTo(CERO);
        }
    }

    // Feature: crm-anuncios-luminosos, Property 9: Para cualquier secuencia de Movimiento_Inventario sobre un Material, las existencias resultantes equivalen a la suma con signo de los movimientos aplicados y nunca son negativas; una salida que dejaria las existencias por debajo de 0 se rechaza y conserva las existencias sin cambios.
    @Property(tries = 1000)
    void salidaQueExcedeElSaldoSeRechazaYConservaLasExistencias(
            @ForAll("stockBase") BigDecimal base,
            @ForAll("cantidadesPositivas") BigDecimal exceso) {

        Material material = Material.crear("Tubo", "pieza", CERO, ACTOR);
        // Sembrar un stock base con una entrada inicial.
        material.aplicarMovimiento(TipoMovimientoInventario.ENTRADA, base, ACTOR);
        BigDecimal saldo = material.getExistencias();

        // Una salida estrictamente mayor que el saldo dejaria el resultado < 0.
        BigDecimal cantidadSalida = saldo.add(exceso).setScale(ESCALA, RoundingMode.HALF_UP);

        assertThatThrownBy(() ->
                material.aplicarMovimiento(TipoMovimientoInventario.SALIDA, cantidadSalida, ACTOR))
                .as("una salida de %s sobre un saldo de %s debe rechazarse por insuficiencia",
                        cantidadSalida, saldo)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("existencias insuficientes");

        assertThat(material.getExistencias())
                .as("tras una salida rechazada, las existencias se conservan sin cambios")
                .isEqualByComparingTo(saldo);
    }

    // Feature: crm-anuncios-luminosos, Property 9: Para cualquier secuencia de Movimiento_Inventario sobre un Material, las existencias resultantes equivalen a la suma con signo de los movimientos aplicados y nunca son negativas; una salida que dejaria las existencias por debajo de 0 se rechaza y conserva las existencias sin cambios.
    @Property(tries = 1000)
    void ajusteNegativoQueExcedeElSaldoSeRechazaYConservaLasExistencias(
            @ForAll("stockBase") BigDecimal base,
            @ForAll("cantidadesPositivas") BigDecimal exceso) {

        Material material = Material.crear("Perfil", "metro", CERO, ACTOR);
        material.aplicarMovimiento(TipoMovimientoInventario.ENTRADA, base, ACTOR);
        BigDecimal saldo = material.getExistencias();

        // Un ajuste negativo cuyo valor absoluto supera el saldo deja el resultado < 0.
        BigDecimal ajuste = saldo.add(exceso).negate().setScale(ESCALA, RoundingMode.HALF_UP);

        assertThatThrownBy(() ->
                material.aplicarMovimiento(TipoMovimientoInventario.AJUSTE, ajuste, ACTOR))
                .as("un ajuste de %s sobre un saldo de %s debe rechazarse por insuficiencia",
                        ajuste, saldo)
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(material.getExistencias())
                .as("tras un ajuste negativo rechazado, las existencias se conservan sin cambios")
                .isEqualByComparingTo(saldo);
    }

    // Feature: crm-anuncios-luminosos, Property 9: Para cualquier secuencia de Movimiento_Inventario sobre un Material, las existencias resultantes equivalen a la suma con signo de los movimientos aplicados y nunca son negativas; una salida que dejaria las existencias por debajo de 0 se rechaza y conserva las existencias sin cambios.
    @Property(tries = 1000)
    void entradaSalidaExactaHastaCeroSeAceptaYNoQuedaNegativa(
            @ForAll("cantidadesPositivas") BigDecimal cantidad) {

        Material material = Material.crear("Lona", "metro", CERO, ACTOR);
        ResultadoMovimiento entrada =
                material.aplicarMovimiento(TipoMovimientoInventario.ENTRADA, cantidad, ACTOR);
        assertThat(entrada.existenciasResultantes())
                .as("la entrada debe sumar exactamente la cantidad")
                .isEqualByComparingTo(cantidad.setScale(ESCALA, RoundingMode.HALF_UP));

        // Una salida por el saldo exacto lleva las existencias a 0 (borde no negativo).
        ResultadoMovimiento salida =
                material.aplicarMovimiento(TipoMovimientoInventario.SALIDA, cantidad, ACTOR);
        assertThat(salida.existenciasResultantes())
                .as("una salida por el saldo exacto debe dejar las existencias en 0")
                .isEqualByComparingTo(CERO);
        assertThat(salida.existenciasResultantes())
                .as("las existencias en el borde no deben ser negativas")
                .isGreaterThanOrEqualTo(CERO);
    }

    /** Cantidades positivas expuestas como generador con nombre para @ForAll. */
    @Provide
    Arbitrary<BigDecimal> cantidadesPositivas() {
        return cantidadPositiva();
    }
}
