package com.dessti.crm.comercial.producto.domain;

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
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 30: Rango de
 * precios de Producto</strong> (Req 59.3, 59.10).
 *
 * <p>Reutiliza la pieza de produccion
 * {@link CatalogoValidaciones#validarPrecio(BigDecimal)} como pieza pura, sin
 * base de datos ni contexto de Spring. La property comprueba universalmente el
 * invariante de rango cerrado {@code [0.01, 999,999,999.99]} sobre el precio de
 * Producto definido en una Lista_Precios: el precio se acepta si y solo si su
 * representacion monetaria efectiva (escala 2, redondeo HALF_UP) queda dentro
 * del rango; un precio fuera de rango se rechaza con
 * {@link ReglaNegocioException} (422) y no se normaliza para persistir.</p>
 *
 * <h2>Semantica espejo de produccion</h2>
 * <p>El redondeo a escala 2 (HALF_UP) se aplica <em>antes</em> de la
 * comprobacion de rango. Por tanto un valor con mas de 2 decimales se evalua por
 * su valor redondeado: {@code 0.004 -> 0.00} (rechazado), {@code 0.005 -> 0.01}
 * (aceptado), {@code 999,999,999.994 -> 999,999,999.99} (aceptado) y
 * {@code 999,999,999.995 -> 1,000,000,000.00} (rechazado). Estos casos frontera
 * se afirman de forma dirigida ademas de las propiedades universales.</p>
 */
class RangoPreciosProductoPropertyTest {

    private static final BigDecimal MINIMO = CatalogoValidaciones.PRECIO_MINIMO;      // 0.01
    private static final BigDecimal MAXIMO = CatalogoValidaciones.PRECIO_MAXIMO;      // 999999999.99
    private static final int ESCALA = CatalogoValidaciones.ESCALA_MONETARIA;          // 2

    /** Maximo numero de centavos aceptado: 999,999,999.99 == 99_999_999_999 centavos. */
    private static final long MAX_CENTAVOS = 99_999_999_999L;

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Precios <em>dentro de rango</em> con escala 2 exacta: se generan a partir
     * de un numero entero de centavos en {@code [1, 99_999_999_999]} y se
     * dividen entre 100, cubriendo de forma uniforme todo {@code [0.01, MAXIMO]}.
     */
    @Provide
    Arbitrary<BigDecimal> preciosEnRango() {
        return Arbitraries.longs()
                .between(1L, MAX_CENTAVOS)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
    }

    /**
     * Precios que, tras redondear a escala 2, quedan estrictamente <em>por
     * debajo</em> del minimo. Se generan valores en {@code [-1_000_000, 0.004]}
     * aproximadamente: cero, negativos y positivos que redondean a {@code 0.00}.
     */
    @Provide
    Arbitrary<BigDecimal> preciosDebajoDeRango() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-1000000.00"), new BigDecimal("0.004"))
                .ofScale(3);
    }

    /**
     * Precios que, tras redondear a escala 2, quedan estrictamente <em>por
     * encima</em> del maximo: desde {@code 1,000,000,000.00} hasta un valor muy
     * grande.
     */
    @Provide
    Arbitrary<BigDecimal> preciosEncimaDeRango() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1000000000.00"), new BigDecimal("9999999999999.99"))
                .ofScale(2);
    }

    // ----------------------------------------------------------------------
    // Property 30 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 30: Para cualquier precio de Producto definido en una Lista_Precios, el precio se acepta si y solo si esta dentro del rango 0.01..999,999,999.99; un precio fuera de rango se rechaza y no se persiste.
    @Property(tries = 1000)
    void precioEnRangoSeAceptaYNormalizaAEscala2(@ForAll("preciosEnRango") BigDecimal precio) {
        BigDecimal resultado = CatalogoValidaciones.validarPrecio(precio);

        // Se normaliza a la representacion monetaria (escala 2, HALF_UP).
        assertThat(resultado)
                .as("el precio aceptado debe normalizarse a escala 2 (HALF_UP)")
                .isEqualByComparingTo(precio.setScale(ESCALA, RoundingMode.HALF_UP));
        assertThat(resultado.scale())
                .as("el precio normalizado debe tener escala 2")
                .isEqualTo(ESCALA);
        // Y queda dentro del rango cerrado [0.01, 999,999,999.99].
        assertThat(resultado)
                .as("el precio aceptado debe quedar dentro de [%s, %s]", MINIMO, MAXIMO)
                .isBetween(MINIMO, MAXIMO);
    }

    // Feature: crm-anuncios-luminosos, Property 30: Para cualquier precio de Producto definido en una Lista_Precios, el precio se acepta si y solo si esta dentro del rango 0.01..999,999,999.99; un precio fuera de rango se rechaza y no se persiste.
    @Property(tries = 1000)
    void precioDebajoDeRangoSeRechaza(@ForAll("preciosDebajoDeRango") BigDecimal precio) {
        // Precondicion del generador: al redondear a escala 2, queda por debajo del minimo.
        BigDecimal normalizado = precio.setScale(ESCALA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(MINIMO) >= 0) {
            return; // no aplica (el borde 0.005 redondea a 0.01, cubierto en fronteras)
        }

        assertThatThrownBy(() -> CatalogoValidaciones.validarPrecio(precio))
                .as("un precio que redondea por debajo de %s debe rechazarse", MINIMO)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 30: Para cualquier precio de Producto definido en una Lista_Precios, el precio se acepta si y solo si esta dentro del rango 0.01..999,999,999.99; un precio fuera de rango se rechaza y no se persiste.
    @Property(tries = 1000)
    void precioEncimaDeRangoSeRechaza(@ForAll("preciosEncimaDeRango") BigDecimal precio) {
        // Precondicion del generador: al redondear a escala 2, queda por encima del maximo.
        BigDecimal normalizado = precio.setScale(ESCALA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(MAXIMO) <= 0) {
            return; // no aplica
        }

        assertThatThrownBy(() -> CatalogoValidaciones.validarPrecio(precio))
                .as("un precio que redondea por encima de %s debe rechazarse", MAXIMO)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 30: Para cualquier precio de Producto definido en una Lista_Precios, el precio se acepta si y solo si esta dentro del rango 0.01..999,999,999.99; un precio fuera de rango se rechaza y no se persiste.
    @Property(tries = 1000)
    void redondeoHalfUpSeAplicaAntesDeLaComprobacionDeRango(
            @ForAll("preciosArbitrarios") BigDecimal precio) {
        // Modelo de referencia: redondear primero, comprobar rango despues.
        BigDecimal esperado = precio.setScale(ESCALA, RoundingMode.HALF_UP);
        boolean deberiaAceptarse =
                esperado.compareTo(MINIMO) >= 0 && esperado.compareTo(MAXIMO) <= 0;

        if (deberiaAceptarse) {
            BigDecimal resultado = CatalogoValidaciones.validarPrecio(precio);
            assertThat(resultado)
                    .as("aceptado: el resultado debe ser el valor redondeado HALF_UP a escala 2")
                    .isEqualByComparingTo(esperado);
            assertThat(resultado.scale()).isEqualTo(ESCALA);
        } else {
            assertThatThrownBy(() -> CatalogoValidaciones.validarPrecio(precio))
                    .as("fuera de rango tras redondear (%s -> %s) debe rechazarse", precio, esperado)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    /** Precios arbitrarios (escala hasta 6) para ejercitar el redondeo antes del rango. */
    @Provide
    Arbitrary<BigDecimal> preciosArbitrarios() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-10.00"), new BigDecimal("1000000010.00"))
                .ofScale(6);
    }

    // Feature: crm-anuncios-luminosos, Property 30: Para cualquier precio de Producto definido en una Lista_Precios, el precio se acepta si y solo si esta dentro del rango 0.01..999,999,999.99; un precio fuera de rango se rechaza y no se persiste.
    @Property(tries = 1000)
    void fronterasExactasYDeRedondeoSeComportanSegunProduccion(
            @ForAll("valoresFrontera") BigDecimal precio) {
        BigDecimal esperado = precio.setScale(ESCALA, RoundingMode.HALF_UP);
        boolean deberiaAceptarse =
                esperado.compareTo(MINIMO) >= 0 && esperado.compareTo(MAXIMO) <= 0;

        if (deberiaAceptarse) {
            assertThat(CatalogoValidaciones.validarPrecio(precio))
                    .as("frontera aceptada %s -> %s", precio, esperado)
                    .isEqualByComparingTo(esperado);
        } else {
            assertThatThrownBy(() -> CatalogoValidaciones.validarPrecio(precio))
                    .as("frontera rechazada %s -> %s", precio, esperado)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    /**
     * Conjunto dirigido de valores frontera y de redondeo (data-driven):
     * bordes exactos del rango y casos HALF_UP a ambos extremos.
     */
    @Provide
    Arbitrary<BigDecimal> valoresFrontera() {
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
