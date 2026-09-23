package com.dessti.crm.facturacion.notacredito.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 14: Nota de credito
 * acotada por el saldo de la factura</strong> (Req 37.2).
 *
 * <p>Reutiliza la pieza de produccion pura
 * {@link NotaCreditoValidaciones#validarMontoContraSaldo(BigDecimal, BigDecimal, BigDecimal)}
 * sin base de datos ni contexto de Spring. El saldo disponible se modela como
 * {@code total - Σ notas previas}; se verifica universalmente que un monto dentro
 * del saldo se admite y que un monto que lo excede se rechaza, sin que nunca se
 * permita rebasar el saldo.</p>
 *
 * <h2>Invariantes verificados (Property 14)</h2>
 * <ul>
 *   <li>Para cualquier {@code 0 < monto <= saldo}, la validacion lo acepta y
 *       devuelve el monto normalizado a escala 2.</li>
 *   <li>Para cualquier {@code monto > saldo}, la validacion lo rechaza con 422.</li>
 *   <li>Un monto no positivo (0 o negativo) siempre se rechaza.</li>
 *   <li>El saldo disponible nunca es negativo y es {@code round(total - previas, 2)}.</li>
 * </ul>
 */
class NotaCreditoAcotadaPorSaldoPropertyTest {

    private static final int ESCALA = NotaCreditoValidaciones.ESCALA_MONETARIA;   // 2
    private static final long MAX_CENTAVOS = 99_999_999_999L;   // 999,999,999.99

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Importe monetario con escala 2 en {@code [0.00, 999,999,999.99]}. */
    private static Arbitrary<BigDecimal> importes() {
        return Arbitraries.longs()
                .between(0L, MAX_CENTAVOS)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
    }

    /**
     * Escenario de Factura: total y notas previas (acotadas al total) que definen un
     * saldo disponible no negativo.
     */
    @Provide
    Arbitrary<Escenario> escenarios() {
        return importes().flatMap(total -> {
            long totalCentavos = total.movePointRight(ESCALA).longValueExact();
            Arbitrary<BigDecimal> previas = Arbitraries.longs()
                    .between(0L, totalCentavos)
                    .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA));
            return previas.map(p -> new Escenario(total, p));
        });
    }

    /** Escenario + una fraccion en centavos para elegir un monto <= o > que el saldo. */
    @Provide
    Arbitrary<CasoMonto> casosMonto() {
        return Combinators.combine(escenarios(), Arbitraries.longs().between(0L, MAX_CENTAVOS))
                .as(CasoMonto::new);
    }

    // ----------------------------------------------------------------------
    // Property 14 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 14: Nota de crédito acotada por el saldo de la factura
    @Property(tries = 1000)
    void saldoDisponibleEsTotalMenosPreviasYNuncaNegativo(@ForAll("escenarios") Escenario escenario) {
        BigDecimal saldo = NotaCreditoValidaciones.saldoDisponible(escenario.total, escenario.previas);

        BigDecimal esperado = escenario.total.subtract(escenario.previas)
                .setScale(ESCALA, RoundingMode.HALF_UP);

        assertThat(saldo).as("saldo == round(total - previas, 2)").isEqualByComparingTo(esperado);
        assertThat(saldo.signum()).as("el saldo nunca es negativo").isGreaterThanOrEqualTo(0);
    }

    // Feature: crm-anuncios-luminosos, Property 14: Nota de crédito acotada por el saldo de la factura
    @Property(tries = 1000)
    void montoDentroDelSaldoSeAdmite(@ForAll("casosMonto") CasoMonto caso) {
        BigDecimal saldo = NotaCreditoValidaciones.saldoDisponible(caso.escenario.total, caso.escenario.previas);
        long saldoCentavos = saldo.movePointRight(ESCALA).longValueExact();

        // Solo tiene sentido si hay saldo positivo: se elige un monto en (0, saldo].
        if (saldoCentavos <= 0) {
            return;
        }
        long montoCentavos = (caso.semilla % saldoCentavos) + 1;   // 1..saldoCentavos
        BigDecimal monto = new BigDecimal(montoCentavos).movePointLeft(ESCALA);

        BigDecimal aceptado = NotaCreditoValidaciones.validarMontoContraSaldo(
                monto, caso.escenario.total, caso.escenario.previas);

        assertThat(aceptado)
                .as("un monto 0 < monto <= saldo se admite y se normaliza a escala 2")
                .isEqualByComparingTo(monto);
        assertThat(aceptado).isLessThanOrEqualTo(saldo);
    }

    // Feature: crm-anuncios-luminosos, Property 14: Nota de crédito acotada por el saldo de la factura
    @Property(tries = 1000)
    void montoQueExcedeElSaldoSeRechaza(@ForAll("casosMonto") CasoMonto caso) {
        BigDecimal saldo = NotaCreditoValidaciones.saldoDisponible(caso.escenario.total, caso.escenario.previas);
        long saldoCentavos = saldo.movePointRight(ESCALA).longValueExact();

        // Monto estrictamente mayor que el saldo (saldo + 1 centavo o mas).
        long excesoCentavos = saldoCentavos + 1 + (caso.semilla % 1000);
        BigDecimal monto = new BigDecimal(excesoCentavos).movePointLeft(ESCALA);

        assertThatThrownBy(() -> NotaCreditoValidaciones.validarMontoContraSaldo(
                monto, caso.escenario.total, caso.escenario.previas))
                .as("un monto > saldo se rechaza (nunca permite rebasar el saldo)")
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("excede el saldo disponible");
    }

    // Feature: crm-anuncios-luminosos, Property 14: Nota de crédito acotada por el saldo de la factura
    @Property(tries = 1000)
    void montoNoPositivoSeRechaza(@ForAll("escenarios") Escenario escenario,
                                  @ForAll long semilla) {
        long centavos = -(Math.floorMod(semilla, MAX_CENTAVOS));   // <= 0
        BigDecimal monto = new BigDecimal(centavos).movePointLeft(ESCALA);

        assertThatThrownBy(() -> NotaCreditoValidaciones.validarMontoContraSaldo(
                monto, escenario.total, escenario.previas))
                .as("un monto no positivo siempre se rechaza")
                .isInstanceOf(ReglaNegocioException.class);
    }

    /** Escenario de Factura: total y suma de notas previas (previas <= total). */
    private static final class Escenario {
        private final BigDecimal total;
        private final BigDecimal previas;

        private Escenario(BigDecimal total, BigDecimal previas) {
            this.total = total;
            this.previas = previas;
        }
    }

    /** Escenario + semilla para derivar un monto dentro o fuera del saldo. */
    private static final class CasoMonto {
        private final Escenario escenario;
        private final long semilla;

        private CasoMonto(Escenario escenario, Long semilla) {
            this.escenario = escenario;
            this.semilla = Math.floorMod(semilla, MAX_CENTAVOS);
        }
    }
}
