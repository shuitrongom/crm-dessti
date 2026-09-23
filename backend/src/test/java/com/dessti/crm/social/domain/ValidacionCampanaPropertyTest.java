package com.dessti.crm.social.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.time.api.Dates;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 39: Validacion de
 * presupuesto y periodo de la Campana_Publicitaria</strong> (Valida los Requisitos
 * 65.7 y 65.8).
 *
 * <p>Reutiliza las piezas puras de produccion {@link ValidacionCampana#validar}
 * (presupuesto en {@code [0.01, 999,999,999.99]} tras redondeo HALF_UP a escala 2, y
 * periodo {@code fecha_fin >= fecha_inicio}), sin base de datos ni contexto de
 * Spring. Las properties comprueban universalmente el invariante: un presupuesto y
 * periodo validos se aceptan (sin excepcion) devolviendo el presupuesto normalizado;
 * un presupuesto fuera de rango O una {@code fecha_fin} anterior a {@code fecha_inicio}
 * se rechazan con {@link ReglaNegocioException} (422) y, por tanto, la campaña no se
 * persiste (Req 65.8).</p>
 *
 * <h2>Semantica de rango del presupuesto</h2>
 * <p>El redondeo a escala 2 (HALF_UP) se aplica <em>antes</em> de la comprobacion de
 * rango, con identica semantica a {@code CotizacionValidaciones.validarPrecioUnitario}.
 * Los casos frontera exactos (0.00, 0.01, 999,999,999.99, 1,000,000,000.00) se afirman
 * de forma dirigida ademas de las propiedades universales.</p>
 */
class ValidacionCampanaPropertyTest {

    private static final BigDecimal PRESUPUESTO_MINIMO = ValidacionCampana.PRESUPUESTO_MINIMO; // 0.01
    private static final BigDecimal PRESUPUESTO_MAXIMO = ValidacionCampana.PRESUPUESTO_MAXIMO; // 999999999.99
    private static final int ESCALA = ValidacionCampana.ESCALA_MONETARIA;                      // 2

    /** Maximo numero de centavos aceptado: 999,999,999.99 == 99_999_999_999 centavos. */
    private static final long MAX_CENTAVOS = 99_999_999_999L;

    // ----------------------------------------------------------------------
    // Generadores — presupuesto
    // ----------------------------------------------------------------------

    /**
     * Presupuestos <em>dentro de rango</em> con escala 2 exacta: se generan a partir
     * de un numero entero de centavos en {@code [1, 99_999_999_999]} y se dividen
     * entre 100, cubriendo uniformemente todo {@code [0.01, MAXIMO]}.
     */
    @Provide
    Arbitrary<BigDecimal> presupuestosEnRango() {
        return Arbitraries.longs()
                .between(1L, MAX_CENTAVOS)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
    }

    /**
     * Presupuestos que, tras redondear a escala 2, quedan estrictamente <em>por
     * debajo</em> del minimo: cero, negativos y positivos que redondean a {@code 0.00}.
     */
    @Provide
    Arbitrary<BigDecimal> presupuestosDebajoDeRango() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-1000000.00"), new BigDecimal("0.004"))
                .ofScale(3);
    }

    /**
     * Presupuestos que, tras redondear a escala 2, quedan estrictamente <em>por
     * encima</em> del maximo.
     */
    @Provide
    Arbitrary<BigDecimal> presupuestosEncimaDeRango() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1000000000.00"), new BigDecimal("9999999999999.99"))
                .ofScale(2);
    }

    /** Presupuestos arbitrarios (escala hasta 6) para ejercitar el redondeo antes del rango. */
    @Provide
    Arbitrary<BigDecimal> presupuestosArbitrarios() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-10.00"), new BigDecimal("1000000010.00"))
                .ofScale(6);
    }

    /** Conjunto dirigido de valores frontera del presupuesto. */
    @Provide
    Arbitrary<BigDecimal> valoresFronteraPresupuesto() {
        return Arbitraries.of(
                new BigDecimal("0.00"),           // -> rechazado
                new BigDecimal("0.01"),           // -> aceptado (borde inferior)
                new BigDecimal("999999999.99"),   // -> aceptado (borde superior)
                new BigDecimal("1000000000.00"),  // -> rechazado
                new BigDecimal("0.005"),          // -> 0.01 aceptado
                new BigDecimal("0.004"),          // -> 0.00 rechazado
                new BigDecimal("999999999.994"),  // -> 999999999.99 aceptado
                new BigDecimal("999999999.995"),  // -> 1000000000.00 rechazado
                new BigDecimal("-0.01")           // -> rechazado
        );
    }

    // ----------------------------------------------------------------------
    // Generadores — fechas del periodo
    // ----------------------------------------------------------------------

    /** Fechas de un rango amplio para construir periodos. */
    @Provide
    Arbitrary<LocalDate> fechas() {
        return Dates.dates().between(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31));
    }

    /** Periodos validos: pares (inicio, fin) con {@code fin >= inicio}. */
    @Provide
    Arbitrary<LocalDate[]> periodosValidos() {
        return Combinators.combine(fechas(), fechas())
                .as((a, b) -> a.isAfter(b) ? new LocalDate[]{b, a} : new LocalDate[]{a, b});
    }

    /** Periodos invalidos: pares (inicio, fin) con {@code fin < inicio} estricto. */
    @Provide
    Arbitrary<LocalDate[]> periodosInvalidos() {
        return Combinators.combine(fechas(), fechas())
                .as((a, b) -> new LocalDate[]{a, b})
                .filter(par -> par[1].isBefore(par[0]));
    }

    // ----------------------------------------------------------------------
    // Property 39 — presupuesto y periodo validos => se acepta
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 39: Validación de presupuesto y periodo de la Campaña_Publicitaria
    @Property(tries = 1000)
    void presupuestoEnRangoYPeriodoValidoSeAcepta(
            @ForAll("presupuestosEnRango") BigDecimal presupuesto,
            @ForAll("periodosValidos") LocalDate[] periodo) {
        BigDecimal esperado = presupuesto.setScale(ESCALA, RoundingMode.HALF_UP);

        BigDecimal resultado = ValidacionCampana.validar(presupuesto, periodo[0], periodo[1]);

        assertThat(resultado)
                .as("presupuesto en [%s, %s] con periodo valido debe aceptarse y normalizarse",
                        PRESUPUESTO_MINIMO, PRESUPUESTO_MAXIMO)
                .isEqualByComparingTo(esperado);
        assertThat(resultado.scale())
                .as("el presupuesto aceptado debe tener escala 2")
                .isEqualTo(ESCALA);
        assertThat(resultado)
                .as("el presupuesto aceptado queda dentro de [%s, %s]", PRESUPUESTO_MINIMO, PRESUPUESTO_MAXIMO)
                .isBetween(PRESUPUESTO_MINIMO, PRESUPUESTO_MAXIMO);
    }

    // ----------------------------------------------------------------------
    // Property 39 — presupuesto fuera de rango => se rechaza
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 39: Validación de presupuesto y periodo de la Campaña_Publicitaria
    @Property(tries = 1000)
    void presupuestoDebajoDeRangoSeRechaza(
            @ForAll("presupuestosDebajoDeRango") BigDecimal presupuesto,
            @ForAll("periodosValidos") LocalDate[] periodo) {
        BigDecimal normalizado = presupuesto.setScale(ESCALA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(PRESUPUESTO_MINIMO) >= 0) {
            return; // el borde 0.005 redondea a 0.01: cubierto en fronteras dirigidas
        }
        assertThatThrownBy(() -> ValidacionCampana.validar(presupuesto, periodo[0], periodo[1]))
                .as("un presupuesto que redondea por debajo de %s debe rechazarse", PRESUPUESTO_MINIMO)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 39: Validación de presupuesto y periodo de la Campaña_Publicitaria
    @Property(tries = 1000)
    void presupuestoEncimaDeRangoSeRechaza(
            @ForAll("presupuestosEncimaDeRango") BigDecimal presupuesto,
            @ForAll("periodosValidos") LocalDate[] periodo) {
        BigDecimal normalizado = presupuesto.setScale(ESCALA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(PRESUPUESTO_MAXIMO) <= 0) {
            return; // no aplica
        }
        assertThatThrownBy(() -> ValidacionCampana.validar(presupuesto, periodo[0], periodo[1]))
                .as("un presupuesto que redondea por encima de %s debe rechazarse", PRESUPUESTO_MAXIMO)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // ----------------------------------------------------------------------
    // Property 39 — periodo invalido (fin < inicio) => se rechaza
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 39: Validación de presupuesto y periodo de la Campaña_Publicitaria
    @Property(tries = 1000)
    void periodoConFinAnteriorAInicioSeRechaza(
            @ForAll("presupuestosEnRango") BigDecimal presupuesto,
            @ForAll("periodosInvalidos") LocalDate[] periodo) {
        assertThatThrownBy(() -> ValidacionCampana.validar(presupuesto, periodo[0], periodo[1]))
                .as("un periodo con fecha_fin (%s) anterior a fecha_inicio (%s) debe rechazarse",
                        periodo[1], periodo[0])
                .isInstanceOf(ReglaNegocioException.class);
    }

    // ----------------------------------------------------------------------
    // Property 39 — modelo de referencia: se acepta si y solo si ambas reglas se cumplen
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 39: Validación de presupuesto y periodo de la Campaña_Publicitaria
    @Property(tries = 1000)
    void seAceptaSiYSoloSiPresupuestoEnRangoYPeriodoValido(
            @ForAll("presupuestosArbitrarios") BigDecimal presupuesto,
            @ForAll("fechas") LocalDate inicio,
            @ForAll("fechas") LocalDate fin) {
        BigDecimal esperado = presupuesto.setScale(ESCALA, RoundingMode.HALF_UP);
        boolean presupuestoValido =
                esperado.compareTo(PRESUPUESTO_MINIMO) >= 0 && esperado.compareTo(PRESUPUESTO_MAXIMO) <= 0;
        boolean periodoValido = !fin.isBefore(inicio);

        if (presupuestoValido && periodoValido) {
            BigDecimal resultado = ValidacionCampana.validar(presupuesto, inicio, fin);
            assertThat(resultado)
                    .as("aceptado: presupuesto normalizado HALF_UP a escala 2")
                    .isEqualByComparingTo(esperado);
        } else {
            assertThatThrownBy(() -> ValidacionCampana.validar(presupuesto, inicio, fin))
                    .as("rechazado: presupuestoValido=%s periodoValido=%s", presupuestoValido, periodoValido)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    // ----------------------------------------------------------------------
    // Property 39 — fronteras dirigidas del presupuesto (periodo valido fijo)
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 39: Validación de presupuesto y periodo de la Campaña_Publicitaria
    @Property(tries = 1000)
    void fronterasDelPresupuestoSeComportanSegunProduccion(
            @ForAll("valoresFronteraPresupuesto") BigDecimal presupuesto) {
        LocalDate inicio = LocalDate.of(2025, 1, 1);
        LocalDate fin = LocalDate.of(2025, 12, 31);
        BigDecimal esperado = presupuesto.setScale(ESCALA, RoundingMode.HALF_UP);
        boolean deberiaAceptarse =
                esperado.compareTo(PRESUPUESTO_MINIMO) >= 0 && esperado.compareTo(PRESUPUESTO_MAXIMO) <= 0;

        if (deberiaAceptarse) {
            assertThat(ValidacionCampana.validar(presupuesto, inicio, fin))
                    .as("frontera aceptada %s -> %s", presupuesto, esperado)
                    .isEqualByComparingTo(esperado);
        } else {
            assertThatThrownBy(() -> ValidacionCampana.validar(presupuesto, inicio, fin))
                    .as("frontera rechazada %s -> %s", presupuesto, esperado)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    // ----------------------------------------------------------------------
    // Property 39 — periodo de un solo dia (fin == inicio) se acepta
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 39: Validación de presupuesto y periodo de la Campaña_Publicitaria
    @Property(tries = 1000)
    void periodoDeUnSoloDiaSeAcepta(
            @ForAll("presupuestosEnRango") BigDecimal presupuesto,
            @ForAll("fechas") LocalDate dia) {
        assertThatCode(() -> ValidacionCampana.validar(presupuesto, dia, dia))
                .as("un periodo con fecha_fin == fecha_inicio (%s) es valido", dia)
                .doesNotThrowAnyException();
    }
}
