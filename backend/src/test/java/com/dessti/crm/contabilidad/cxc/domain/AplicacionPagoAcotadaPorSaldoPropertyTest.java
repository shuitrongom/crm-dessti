package com.dessti.crm.contabilidad.cxc.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 13: Aplicacion de
 * pago de cliente acotada por el saldo</strong> (Req 36.2, 36.3).
 *
 * <p>Ejercita directamente la regla de negocio PURA
 * {@link CuentaPorCobrar#aplicarPago(BigDecimal, String)} sobre la entidad de
 * dominio, sin base de datos ni contexto de Spring: la CxC se crea con la fabrica
 * {@link CuentaPorCobrar#paraFactura(UUID, UUID, BigDecimal, String)} y se
 * ejercita su regla, que es determinista y sin efectos de framework. Por eso las
 * invariantes de acotamiento por el saldo, no negatividad y coherencia
 * estado&harr;saldo se comprueban universalmente sobre entradas arbitrarias.</p>
 *
 * <h2>Invariantes verificados (Property 13)</h2>
 * <ol>
 *   <li><strong>Pago valido:</strong> con {@code 0 < monto <= saldo}, el saldo
 *       disminuye exactamente por el monto (escala 2) y nunca es negativo (Req 36.2).</li>
 *   <li><strong>Exceso rechazado:</strong> con {@code monto > saldo}, se rechaza
 *       con {@link ReglaNegocioException} (mensaje "excede el saldo") conservando el
 *       saldo SIN mutar (Req 36.3).</li>
 *   <li><strong>Monto no positivo:</strong> {@code monto <= 0} se rechaza con
 *       {@link ReglaNegocioException}.</li>
 *   <li><strong>Secuencia de parcialidades:</strong> una serie de pagos validos
 *       nunca lleva el saldo por debajo de 0 y transita el estado
 *       {@code pendiente -> parcial -> pagada} de forma coherente con el saldo.</li>
 * </ol>
 *
 * <h2>Convenciones numericas (espejo de produccion)</h2>
 * <p>Montos a escala {@value CuentaPorCobrar#ESCALA_MONETARIA} con redondeo
 * {@code HALF_UP}, coherentes con {@code NUMERIC(18,2)} de V31. Los generadores
 * construyen montos a partir de enteros escalados (centavos) para mantener
 * comparaciones exactas por {@code compareTo}.</p>
 */
class AplicacionPagoAcotadaPorSaldoPropertyTest {

    /** Escala monetaria espejo de produccion. */
    private static final int ESCALA_MONETARIA = CuentaPorCobrar.ESCALA_MONETARIA; // 2

    /** Cero escalado a la escala monetaria para comparaciones exactas. */
    private static final BigDecimal CERO =
            BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

    /** Actor ficticio para las fabricas/mutadores de dominio. */
    private static final String ACTOR = "prueba";

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Total estrictamente positivo a escala 2, en centavos de 1 a 1_000_000_000 (0.01..10000000). */
    private static Arbitrary<BigDecimal> totalPositivo() {
        return Arbitraries.longs()
                .between(1L, 1_000_000_000L)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA_MONETARIA));
    }

    /** Total positivo expuesto como generador con nombre. */
    @Provide
    Arbitrary<BigDecimal> totales() {
        return totalPositivo();
    }

    /** Fraccion en centavos (>= 1) para derivar montos acotados por el saldo. */
    @Provide
    Arbitrary<Long> centavos() {
        return Arbitraries.longs().between(1L, 1_000_000_000L);
    }

    private static CuentaPorCobrar cxcPendiente(BigDecimal total) {
        return CuentaPorCobrar.paraFactura(UUID.randomUUID(), UUID.randomUUID(), total, ACTOR);
    }

    // ----------------------------------------------------------------------
    // Property 13 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 13: Aplicación de pago de cliente acotada por el saldo
    @Property(tries = 1000)
    void pagoValidoRestaExactamenteElMontoYNoEsNegativo(
            @ForAll("totales") BigDecimal total,
            @ForAll("centavos") long centavosMonto) {

        CuentaPorCobrar cxc = cxcPendiente(total);
        BigDecimal saldoInicial = cxc.getSaldo();

        // Monto valido: 0 < monto <= saldo (acotado tomando el minimo con el saldo).
        BigDecimal candidato = new BigDecimal(centavosMonto).movePointLeft(ESCALA_MONETARIA);
        BigDecimal monto = candidato.min(saldoInicial).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        cxc.aplicarPago(monto, ACTOR);

        BigDecimal esperado = saldoInicial.subtract(monto)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        assertThat(cxc.getSaldo())
                .as("nuevo saldo tras pago == saldoInicial(%s) - monto(%s)", saldoInicial, monto)
                .isEqualByComparingTo(esperado);
        assertThat(cxc.getSaldo())
                .as("el saldo tras un pago valido nunca es negativo")
                .isGreaterThanOrEqualTo(CERO);
        assertThat(cxc.getSaldo().scale())
                .as("el saldo debe conservar la escala monetaria 2")
                .isEqualTo(ESCALA_MONETARIA);
    }

    // Feature: crm-anuncios-luminosos, Property 13: Aplicación de pago de cliente acotada por el saldo
    @Property(tries = 1000)
    void pagoValidoDerivaElEstadoDeFormaCoherente(
            @ForAll("totales") BigDecimal total,
            @ForAll("centavos") long centavosMonto) {

        CuentaPorCobrar cxc = cxcPendiente(total);
        BigDecimal saldoInicial = cxc.getSaldo();

        BigDecimal candidato = new BigDecimal(centavosMonto).movePointLeft(ESCALA_MONETARIA);
        BigDecimal monto = candidato.min(saldoInicial).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        cxc.aplicarPago(monto, ACTOR);

        if (cxc.getSaldo().compareTo(CERO) == 0) {
            assertThat(cxc.getEstado())
                    .as("saldo 0 -> pagada")
                    .isEqualTo(EstadoCuentaPorCobrar.PAGADA);
        } else {
            assertThat(cxc.getEstado())
                    .as("0 < saldo < total -> parcial")
                    .isEqualTo(EstadoCuentaPorCobrar.PARCIAL);
        }
    }

    // Feature: crm-anuncios-luminosos, Property 13: Aplicación de pago de cliente acotada por el saldo
    @Property(tries = 1000)
    void pagoQueExcedeElSaldoSeRechazaYConservaElSaldo(
            @ForAll("totales") BigDecimal total,
            @ForAll("centavos") long centavosExceso) {

        CuentaPorCobrar cxc = cxcPendiente(total);
        BigDecimal saldoInicial = cxc.getSaldo();

        // Monto estrictamente mayor que el saldo: saldo + delta (delta > 0).
        BigDecimal delta = new BigDecimal(centavosExceso).movePointLeft(ESCALA_MONETARIA);
        BigDecimal montoExceso = saldoInicial.add(delta).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        assertThatThrownBy(() -> cxc.aplicarPago(montoExceso, ACTOR))
                .as("un pago de %s sobre un saldo de %s debe rechazarse por exceso",
                        montoExceso, saldoInicial)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("excede el saldo");

        // El saldo y el estado se conservan (no se muto nada, Req 36.3).
        assertThat(cxc.getSaldo())
                .as("el saldo se conserva tras un pago rechazado por exceso")
                .isEqualByComparingTo(saldoInicial);
        assertThat(cxc.getEstado())
                .as("el estado se conserva 'pendiente' tras un rechazo por exceso")
                .isEqualTo(EstadoCuentaPorCobrar.PENDIENTE);
    }

    // Feature: crm-anuncios-luminosos, Property 13: Aplicación de pago de cliente acotada por el saldo
    @Property(tries = 1000)
    void pagoConMontoNoPositivoSeRechaza(
            @ForAll("totales") BigDecimal total,
            @ForAll("centavos") long magnitudCentavos) {

        CuentaPorCobrar cxc = cxcPendiente(total);
        BigDecimal magnitud = new BigDecimal(magnitudCentavos).movePointLeft(ESCALA_MONETARIA);

        assertThatThrownBy(() -> cxc.aplicarPago(CERO, ACTOR))
                .as("un pago de monto 0 debe rechazarse")
                .isInstanceOf(ReglaNegocioException.class);

        assertThatThrownBy(() -> cxc.aplicarPago(magnitud.negate(), ACTOR))
                .as("un pago de monto negativo debe rechazarse")
                .isInstanceOf(ReglaNegocioException.class);

        assertThatThrownBy(() -> cxc.aplicarPago(null, ACTOR))
                .as("un pago de monto nulo debe rechazarse")
                .isInstanceOf(ReglaNegocioException.class);
    }

    // Feature: crm-anuncios-luminosos, Property 13: Aplicación de pago de cliente acotada por el saldo
    @Property(tries = 1000)
    void secuenciaDeParcialidadesNuncaLlevaElSaldoBajoCeroYLiquidaCoherentemente(
            @ForAll("totales") BigDecimal total,
            @ForAll("centavos") long particionCentavos) {

        CuentaPorCobrar cxc = cxcPendiente(total);
        BigDecimal saldoInicial = cxc.getSaldo();

        // Tamano de parcialidad acotado al saldo (>= 0.01). Para garantizar la
        // terminacion en un numero razonable de pasos con independencia del total
        // generado, el paso se acota ademas a un minimo del 1% del saldo inicial
        // (siempre >= 0.01), de modo que el numero de parcialidades quede muy por
        // debajo del tope de seguridad. Esto NO debilita la propiedad: sigue
        // ejercitando multiples parcialidades validas y su invariante.
        BigDecimal minimoPaso = saldoInicial.movePointLeft(2)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP)
                .max(new BigDecimal("0.01"));
        BigDecimal paso = new BigDecimal(particionCentavos).movePointLeft(ESCALA_MONETARIA)
                .min(saldoInicial).max(minimoPaso)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        // Se aplican parcialidades de tamano 'paso' mientras el saldo lo supere;
        // en cada paso valido se verifica la no negatividad, el decremento estricto
        // y el estado 'parcial' cuando aun queda saldo (Property 13).
        while (cxc.getSaldo().compareTo(paso) > 0) {
            BigDecimal antes = cxc.getSaldo();
            cxc.aplicarPago(paso, ACTOR);

            assertThat(cxc.getSaldo())
                    .as("el saldo nunca es negativo durante las parcialidades")
                    .isGreaterThanOrEqualTo(CERO);
            assertThat(cxc.getSaldo())
                    .as("cada parcialidad valida disminuye el saldo")
                    .isLessThan(antes);
            assertThat(cxc.getEstado())
                    .as("con saldo pendiente el estado es 'parcial'")
                    .isEqualTo(EstadoCuentaPorCobrar.PARCIAL);
        }

        // Pago final por EXACTAMENTE el saldo remanente: liquida la CxC a 0.
        BigDecimal remanente = cxc.getSaldo();
        assertThat(remanente)
                .as("antes del pago final queda un remanente positivo y acotado por el paso")
                .isGreaterThan(CERO)
                .isLessThanOrEqualTo(paso);
        cxc.aplicarPago(remanente, ACTOR);

        // Al agotar el saldo, la CxC queda liquidada ('pagada').
        assertThat(cxc.getSaldo())
                .as("la secuencia liquida el saldo hasta 0")
                .isEqualByComparingTo(CERO);
        assertThat(cxc.getEstado())
                .as("saldo 0 -> pagada")
                .isEqualTo(EstadoCuentaPorCobrar.PAGADA);
    }
}
