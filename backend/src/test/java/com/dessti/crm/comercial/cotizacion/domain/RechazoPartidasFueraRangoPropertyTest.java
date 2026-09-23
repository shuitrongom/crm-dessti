package com.dessti.crm.comercial.cotizacion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 3: Rechazo de
 * partidas fuera de rango</strong> (Req 6.4, 31.4).
 *
 * <p>Reutiliza las piezas puras de produccion
 * {@link CotizacionValidaciones#validarCantidad(int)} y
 * {@link CotizacionValidaciones#validarPrecioUnitario(BigDecimal)}, sin base de
 * datos ni contexto de Spring. Las properties comprueban universalmente el
 * invariante de rechazo: una partida cuya cantidad este fuera de
 * {@code [1, 999,999]} o cuyo precio unitario efectivo (escala 2, HALF_UP) este
 * fuera de {@code [0.01, 999,999,999.99]} se rechaza con
 * {@link ReglaNegocioException} (422) y, por tanto, no se calcula su subtotal;
 * los valores dentro de rango se aceptan y se devuelven normalizados.</p>
 *
 * <h2>Semantica espejo de produccion</h2>
 * <p>Para el precio unitario, el redondeo a escala 2 (HALF_UP) se aplica
 * <em>antes</em> de la comprobacion de rango, con identica semantica a
 * {@code CatalogoValidaciones.validarPrecio} (Property 30). Asi, un valor con
 * mas de 2 decimales se evalua por su representacion monetaria efectiva:
 * {@code 0.004 -> 0.00} (rechazado), {@code 0.005 -> 0.01} (aceptado),
 * {@code 999,999,999.994 -> 999,999,999.99} (aceptado) y
 * {@code 999,999,999.995 -> 1,000,000,000.00} (rechazado). Estos casos frontera
 * se afirman de forma dirigida ademas de las propiedades universales.</p>
 */
class RechazoPartidasFueraRangoPropertyTest {

    private static final int CANTIDAD_MINIMA = CotizacionValidaciones.CANTIDAD_MINIMA;   // 1
    private static final int CANTIDAD_MAXIMA = CotizacionValidaciones.CANTIDAD_MAXIMA;   // 999999
    private static final BigDecimal PRECIO_MINIMO = CotizacionValidaciones.PRECIO_MINIMO; // 0.01
    private static final BigDecimal PRECIO_MAXIMO = CotizacionValidaciones.PRECIO_MAXIMO; // 999999999.99
    private static final int ESCALA = CotizacionValidaciones.ESCALA_MONETARIA;            // 2

    /** Maximo numero de centavos aceptado: 999,999,999.99 == 99_999_999_999 centavos. */
    private static final long MAX_CENTAVOS = 99_999_999_999L;

    // ----------------------------------------------------------------------
    // Generadores — cantidad
    // ----------------------------------------------------------------------

    /** Cantidades dentro del rango cerrado [1, 999,999]. */
    @Provide
    Arbitrary<Integer> cantidadesEnRango() {
        return Arbitraries.integers().between(CANTIDAD_MINIMA, CANTIDAD_MAXIMA);
    }

    /** Cantidades estrictamente por debajo del minimo: cero y negativos. */
    @Provide
    Arbitrary<Integer> cantidadesDebajoDeRango() {
        return Arbitraries.integers().between(Integer.MIN_VALUE, CANTIDAD_MINIMA - 1);
    }

    /** Cantidades estrictamente por encima del maximo. */
    @Provide
    Arbitrary<Integer> cantidadesEncimaDeRango() {
        return Arbitraries.integers().between(CANTIDAD_MAXIMA + 1, Integer.MAX_VALUE);
    }

    // ----------------------------------------------------------------------
    // Generadores — precio unitario
    // ----------------------------------------------------------------------

    /**
     * Precios <em>dentro de rango</em> con escala 2 exacta: se generan a partir
     * de un numero entero de centavos en {@code [1, 99_999_999_999]} y se
     * dividen entre 100, cubriendo uniformemente todo {@code [0.01, MAXIMO]}.
     */
    @Provide
    Arbitrary<BigDecimal> preciosEnRango() {
        return Arbitraries.longs()
                .between(1L, MAX_CENTAVOS)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
    }

    /**
     * Precios que, tras redondear a escala 2, quedan estrictamente <em>por
     * debajo</em> del minimo: cero, negativos y positivos que redondean a
     * {@code 0.00}.
     */
    @Provide
    Arbitrary<BigDecimal> preciosDebajoDeRango() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-1000000.00"), new BigDecimal("0.004"))
                .ofScale(3);
    }

    /**
     * Precios que, tras redondear a escala 2, quedan estrictamente <em>por
     * encima</em> del maximo.
     */
    @Provide
    Arbitrary<BigDecimal> preciosEncimaDeRango() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1000000000.00"), new BigDecimal("9999999999999.99"))
                .ofScale(2);
    }

    /** Precios arbitrarios (escala hasta 6) para ejercitar el redondeo antes del rango. */
    @Provide
    Arbitrary<BigDecimal> preciosArbitrarios() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-10.00"), new BigDecimal("1000000010.00"))
                .ofScale(6);
    }

    // ----------------------------------------------------------------------
    // Property 3 — Invariantes de cantidad
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 3: Para cualquier partida cuya cantidad esté fuera de 1..999,999 o cuyo precio unitario esté fuera de 0.01..999,999,999.99, el sistema rechaza la partida y no calcula su subtotal.
    @Property(tries = 1000)
    void cantidadEnRangoSeAceptaYSeDevuelveIgual(@ForAll("cantidadesEnRango") int cantidad) {
        int resultado = CotizacionValidaciones.validarCantidad(cantidad);

        assertThat(resultado)
                .as("una cantidad dentro de [%d, %d] debe aceptarse sin cambios",
                        CANTIDAD_MINIMA, CANTIDAD_MAXIMA)
                .isEqualTo(cantidad);
    }

    // Feature: crm-anuncios-luminosos, Property 3: Para cualquier partida cuya cantidad esté fuera de 1..999,999 o cuyo precio unitario esté fuera de 0.01..999,999,999.99, el sistema rechaza la partida y no calcula su subtotal.
    @Property(tries = 1000)
    void cantidadDebajoDeRangoSeRechaza(@ForAll("cantidadesDebajoDeRango") int cantidad) {
        assertThatThrownBy(() -> CotizacionValidaciones.validarCantidad(cantidad))
                .as("una cantidad menor que %d (=%d) debe rechazarse", CANTIDAD_MINIMA, cantidad)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 3: Para cualquier partida cuya cantidad esté fuera de 1..999,999 o cuyo precio unitario esté fuera de 0.01..999,999,999.99, el sistema rechaza la partida y no calcula su subtotal.
    @Property(tries = 1000)
    void cantidadEncimaDeRangoSeRechaza(@ForAll("cantidadesEncimaDeRango") int cantidad) {
        assertThatThrownBy(() -> CotizacionValidaciones.validarCantidad(cantidad))
                .as("una cantidad mayor que %d (=%d) debe rechazarse", CANTIDAD_MAXIMA, cantidad)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 3: Para cualquier partida cuya cantidad esté fuera de 1..999,999 o cuyo precio unitario esté fuera de 0.01..999,999,999.99, el sistema rechaza la partida y no calcula su subtotal.
    @Property(tries = 1000)
    void cantidadSeAceptaSiYSoloSiEstaEnRango(@ForAll("cantidadesArbitrarias") int cantidad) {
        boolean deberiaAceptarse = cantidad >= CANTIDAD_MINIMA && cantidad <= CANTIDAD_MAXIMA;

        if (deberiaAceptarse) {
            assertThat(CotizacionValidaciones.validarCantidad(cantidad))
                    .as("cantidad en rango aceptada sin cambios")
                    .isEqualTo(cantidad);
        } else {
            assertThatThrownBy(() -> CotizacionValidaciones.validarCantidad(cantidad))
                    .as("cantidad fuera de rango (=%d) rechazada", cantidad)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    /** Cantidades arbitrarias que cubren dentro y fuera de rango a ambos extremos. */
    @Provide
    Arbitrary<Integer> cantidadesArbitrarias() {
        return Arbitraries.integers().between(-1000, CANTIDAD_MAXIMA + 1000);
    }

    // ----------------------------------------------------------------------
    // Property 3 — Invariantes de precio unitario
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 3: Para cualquier partida cuya cantidad esté fuera de 1..999,999 o cuyo precio unitario esté fuera de 0.01..999,999,999.99, el sistema rechaza la partida y no calcula su subtotal.
    @Property(tries = 1000)
    void precioEnRangoSeAceptaYNormalizaAEscala2(@ForAll("preciosEnRango") BigDecimal precio) {
        BigDecimal resultado = CotizacionValidaciones.validarPrecioUnitario(precio);

        assertThat(resultado)
                .as("el precio aceptado debe normalizarse a escala 2 (HALF_UP)")
                .isEqualByComparingTo(precio.setScale(ESCALA, RoundingMode.HALF_UP));
        assertThat(resultado.scale())
                .as("el precio normalizado debe tener escala 2")
                .isEqualTo(ESCALA);
        assertThat(resultado)
                .as("el precio aceptado debe quedar dentro de [%s, %s]", PRECIO_MINIMO, PRECIO_MAXIMO)
                .isBetween(PRECIO_MINIMO, PRECIO_MAXIMO);
    }

    // Feature: crm-anuncios-luminosos, Property 3: Para cualquier partida cuya cantidad esté fuera de 1..999,999 o cuyo precio unitario esté fuera de 0.01..999,999,999.99, el sistema rechaza la partida y no calcula su subtotal.
    @Property(tries = 1000)
    void precioDebajoDeRangoSeRechaza(@ForAll("preciosDebajoDeRango") BigDecimal precio) {
        BigDecimal normalizado = precio.setScale(ESCALA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(PRECIO_MINIMO) >= 0) {
            return; // el borde 0.005 redondea a 0.01: cubierto en fronteras dirigidas
        }

        assertThatThrownBy(() -> CotizacionValidaciones.validarPrecioUnitario(precio))
                .as("un precio que redondea por debajo de %s debe rechazarse", PRECIO_MINIMO)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 3: Para cualquier partida cuya cantidad esté fuera de 1..999,999 o cuyo precio unitario esté fuera de 0.01..999,999,999.99, el sistema rechaza la partida y no calcula su subtotal.
    @Property(tries = 1000)
    void precioEncimaDeRangoSeRechaza(@ForAll("preciosEncimaDeRango") BigDecimal precio) {
        BigDecimal normalizado = precio.setScale(ESCALA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(PRECIO_MAXIMO) <= 0) {
            return; // no aplica
        }

        assertThatThrownBy(() -> CotizacionValidaciones.validarPrecioUnitario(precio))
                .as("un precio que redondea por encima de %s debe rechazarse", PRECIO_MAXIMO)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 3: Para cualquier partida cuya cantidad esté fuera de 1..999,999 o cuyo precio unitario esté fuera de 0.01..999,999,999.99, el sistema rechaza la partida y no calcula su subtotal.
    @Property(tries = 1000)
    void redondeoHalfUpSeAplicaAntesDeLaComprobacionDeRango(
            @ForAll("preciosArbitrarios") BigDecimal precio) {
        // Modelo de referencia: redondear primero, comprobar rango despues.
        BigDecimal esperado = precio.setScale(ESCALA, RoundingMode.HALF_UP);
        boolean deberiaAceptarse =
                esperado.compareTo(PRECIO_MINIMO) >= 0 && esperado.compareTo(PRECIO_MAXIMO) <= 0;

        if (deberiaAceptarse) {
            BigDecimal resultado = CotizacionValidaciones.validarPrecioUnitario(precio);
            assertThat(resultado)
                    .as("aceptado: el resultado debe ser el valor redondeado HALF_UP a escala 2")
                    .isEqualByComparingTo(esperado);
            assertThat(resultado.scale()).isEqualTo(ESCALA);
        } else {
            assertThatThrownBy(() -> CotizacionValidaciones.validarPrecioUnitario(precio))
                    .as("fuera de rango tras redondear (%s -> %s) debe rechazarse", precio, esperado)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    // Feature: crm-anuncios-luminosos, Property 3: Para cualquier partida cuya cantidad esté fuera de 1..999,999 o cuyo precio unitario esté fuera de 0.01..999,999,999.99, el sistema rechaza la partida y no calcula su subtotal.
    @Property(tries = 1000)
    void fronterasExactasYDeRedondeoDelPrecioSeComportanSegunProduccion(
            @ForAll("valoresFronteraPrecio") BigDecimal precio) {
        BigDecimal esperado = precio.setScale(ESCALA, RoundingMode.HALF_UP);
        boolean deberiaAceptarse =
                esperado.compareTo(PRECIO_MINIMO) >= 0 && esperado.compareTo(PRECIO_MAXIMO) <= 0;

        if (deberiaAceptarse) {
            assertThat(CotizacionValidaciones.validarPrecioUnitario(precio))
                    .as("frontera aceptada %s -> %s", precio, esperado)
                    .isEqualByComparingTo(esperado);
        } else {
            assertThatThrownBy(() -> CotizacionValidaciones.validarPrecioUnitario(precio))
                    .as("frontera rechazada %s -> %s", precio, esperado)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    /**
     * Conjunto dirigido de valores frontera y de redondeo (data-driven) del
     * precio unitario: bordes exactos del rango y casos HALF_UP a ambos extremos,
     * con la misma semantica que Property 30.
     */
    @Provide
    Arbitrary<BigDecimal> valoresFronteraPrecio() {
        return Arbitraries.of(
                // Bordes exactos: aceptados.
                new BigDecimal("0.01"),
                new BigDecimal("999999999.99"),
                // Redondeo en el borde inferior.
                new BigDecimal("0.005"),   // -> 0.01 aceptado
                new BigDecimal("0.004"),   // -> 0.00 rechazado
                new BigDecimal("0.00"),    // -> 0.00 rechazado
                new BigDecimal("0.014"),   // -> 0.01 aceptado
                // Redondeo en el borde superior.
                new BigDecimal("999999999.994"), // -> 999999999.99 aceptado
                new BigDecimal("999999999.995"), // -> 1000000000.00 rechazado
                // Fuera de rango claros.
                new BigDecimal("-0.01"),         // negativo rechazado
                new BigDecimal("1000000000.00")  // por encima rechazado
        );
    }
}
