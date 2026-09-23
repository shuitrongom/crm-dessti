package com.dessti.crm.tesoreria.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 18: Conciliacion
 * bancaria completa solo con diferencia cero</strong> (Req 43.3, 43.4, 43.5).
 *
 * <p>Ejercita directamente las reglas PURAS
 * {@link ReglasConciliacion#esCompleta(BigDecimal, int)},
 * {@link ReglasConciliacion#calcularDiferencia(BigDecimal, BigDecimal)} y
 * {@link ReglasConciliacion#emparejar(BigDecimal, LocalDate, String, List, int)}, sin
 * base de datos ni contexto de Spring: los metodos son estaticos, sin estado y
 * deterministas, por lo que las invariantes de la conciliacion se comprueban
 * universalmente sobre entradas arbitrarias.</p>
 *
 * <h2>Invariantes verificados (Property 18)</h2>
 * <ol>
 *   <li><strong>Completa &lt;=&gt; diferencia cero y sin excepciones:</strong> con
 *       saldos iguales (diferencia cero) y cero movimientos en excepcion,
 *       {@code esCompleta} es {@code true}; con cualquier diferencia distinta de cero
 *       es {@code false} sin importar las excepciones; con al menos una excepcion es
 *       {@code false} aun con diferencia cero.</li>
 *   <li><strong>Diferencia:</strong> {@code calcularDiferencia} devuelve
 *       exactamente {@code saldoBancario - saldoContable} a escala 2.</li>
 *   <li><strong>Emparejamiento acotado:</strong> el emparejamiento nunca coincide
 *       fuera de la igualdad de monto (en valor absoluto) ni fuera de la tolerancia
 *       de fecha.</li>
 * </ol>
 *
 * <h2>Convenciones numericas (espejo de produccion)</h2>
 * <p>Montos a escala {@value ReglasConciliacion#ESCALA_MONETARIA} con redondeo
 * {@code HALF_UP}, coherentes con {@code NUMERIC(18,2)} de V35. Los generadores
 * construyen montos a partir de enteros escalados para mantener comparaciones
 * exactas por {@code compareTo}.</p>
 */
class ConciliacionBancariaDiferenciaCeroPropertyTest {

    /** Escala monetaria espejo de produccion. */
    private static final int ESCALA_MONETARIA = ReglasConciliacion.ESCALA_MONETARIA; // 2

    /** Cero escalado a la escala monetaria para comparaciones exactas. */
    private static final BigDecimal CERO =
            BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

    /** Fecha base fija para construir fechas deterministas. */
    private static final LocalDate FECHA_BASE = LocalDate.of(2024, 1, 15);

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Monto con signo a escala 2, en centavos de -100_000_000 a 100_000_000 (+/-1_000_000). */
    private static Arbitrary<BigDecimal> montoConSigno() {
        return Arbitraries.longs()
                .between(-100_000_000L, 100_000_000L)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA_MONETARIA));
    }

    /** Monto estrictamente positivo a escala 2, en centavos de 1 a 100_000_000. */
    private static Arbitrary<BigDecimal> montoPositivo() {
        return Arbitraries.longs()
                .between(1L, 100_000_000L)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA_MONETARIA));
    }

    /** Saldo con signo expuesto como generador con nombre. */
    @Provide
    Arbitrary<BigDecimal> saldos() {
        return montoConSigno();
    }

    /** Monto positivo expuesto como generador con nombre. */
    @Provide
    Arbitrary<BigDecimal> montosPositivos() {
        return montoPositivo();
    }

    /** Diferencia estrictamente distinta de cero a escala 2. */
    @Provide
    Arbitrary<BigDecimal> diferenciasNoCero() {
        return Arbitraries.longs()
                .between(-100_000_000L, 100_000_000L)
                .filter(centavos -> centavos != 0L)
                .map(centavos -> new BigDecimal(centavos).movePointLeft(ESCALA_MONETARIA));
    }

    // ----------------------------------------------------------------------
    // Property 18 — Completitud (esCompleta / calcularDiferencia)
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 18: Conciliación bancaria completa solo con diferencia cero
    @Property(tries = 1000)
    void diferenciaCeroYSinExcepcionesEsCompleta(@ForAll("saldos") BigDecimal saldo) {
        // saldoBancario == saldoContable => diferencia cero.
        BigDecimal diferencia = ReglasConciliacion.calcularDiferencia(saldo, saldo);

        assertThat(diferencia)
                .as("saldos iguales producen diferencia cero")
                .isEqualByComparingTo(CERO);
        assertThat(ReglasConciliacion.esCompleta(diferencia, 0))
                .as("con diferencia cero y sin excepciones la conciliacion es COMPLETA")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 18: Conciliación bancaria completa solo con diferencia cero
    @Property(tries = 1000)
    void diferenciaDistintaDeCeroNuncaEsCompleta(
            @ForAll("diferenciasNoCero") BigDecimal diferencia,
            @ForAll @IntRange(min = 0, max = 50) int excepciones) {
        assertThat(ReglasConciliacion.esCompleta(diferencia, excepciones))
                .as("con diferencia %s (distinta de cero) la conciliacion NO es completa, "
                        + "cualquiera sea el numero de excepciones (%s)", diferencia, excepciones)
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 18: Conciliación bancaria completa solo con diferencia cero
    @Property(tries = 1000)
    void movimientoSinExplicarNuncaEsCompletaAunConDiferenciaCero(
            @ForAll("saldos") BigDecimal saldo,
            @ForAll @IntRange(min = 1, max = 50) int excepciones) {
        // Diferencia cero (saldos iguales) pero con al menos una partida sin explicar.
        BigDecimal diferencia = ReglasConciliacion.calcularDiferencia(saldo, saldo);

        assertThat(diferencia)
                .as("saldos iguales producen diferencia cero")
                .isEqualByComparingTo(CERO);
        assertThat(ReglasConciliacion.esCompleta(diferencia, excepciones))
                .as("con %s movimiento(s) en excepcion la conciliacion NO es completa aun con "
                        + "diferencia cero", excepciones)
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 18: Conciliación bancaria completa solo con diferencia cero
    @Property(tries = 1000)
    void diferenciaEsExactamenteBancarioMenosContable(
            @ForAll("saldos") BigDecimal saldoBancario,
            @ForAll("saldos") BigDecimal saldoContable) {
        BigDecimal esperado = saldoBancario.subtract(saldoContable)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        assertThat(ReglasConciliacion.calcularDiferencia(saldoBancario, saldoContable))
                .as("diferencia == saldoBancario(%s) - saldoContable(%s)",
                        saldoBancario, saldoContable)
                .isEqualByComparingTo(esperado);
    }

    // ----------------------------------------------------------------------
    // Property 18 — Emparejamiento acotado (Req 43.3)
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 18: Conciliación bancaria completa solo con diferencia cero
    @Property(tries = 1000)
    void emparejamientoNuncaCoincideConMontoDistinto(
            @ForAll("montosPositivos") BigDecimal magnitudMovimiento,
            @ForAll("montosPositivos") BigDecimal magnitudCandidato,
            @ForAll @IntRange(min = 0, max = 10) int toleranciaDias) {
        // Candidato con misma fecha y referencia, pero magnitud DISTINTA a la del movimiento.
        if (magnitudMovimiento.compareTo(magnitudCandidato) == 0) {
            magnitudCandidato = magnitudCandidato.add(new BigDecimal("0.01"));
        }
        CandidatoConciliacion candidato = CandidatoConciliacion.dePoliza(
                java.util.UUID.randomUUID(), magnitudCandidato, FECHA_BASE, "REF-1");
        List<CandidatoConciliacion> candidatos = List.of(candidato);

        Optional<CandidatoConciliacion> resultado = ReglasConciliacion.emparejar(
                magnitudMovimiento, FECHA_BASE, "REF-1", candidatos, toleranciaDias);

        assertThat(resultado)
                .as("un candidato con monto %s no debe emparejar un movimiento de monto %s",
                        magnitudCandidato, magnitudMovimiento)
                .isEmpty();
    }

    // Feature: crm-anuncios-luminosos, Property 18: Conciliación bancaria completa solo con diferencia cero
    @Property(tries = 1000)
    void emparejamientoNuncaCoincideFueraDeLaToleranciaDeFecha(
            @ForAll("montosPositivos") BigDecimal magnitud,
            @ForAll @IntRange(min = 0, max = 10) int toleranciaDias,
            @ForAll @IntRange(min = 1, max = 30) int desfaseExtra) {
        // Candidato con MISMO monto y referencia, pero fecha fuera de la tolerancia:
        // desfase = tolerancia + desfaseExtra dias (siempre > tolerancia).
        long desfase = (long) toleranciaDias + desfaseExtra;
        LocalDate fechaCandidato = FECHA_BASE.plusDays(desfase);
        CandidatoConciliacion candidato = CandidatoConciliacion.dePoliza(
                java.util.UUID.randomUUID(), magnitud, fechaCandidato, "REF-1");
        List<CandidatoConciliacion> candidatos = List.of(candidato);

        Optional<CandidatoConciliacion> resultado = ReglasConciliacion.emparejar(
                magnitud, FECHA_BASE, "REF-1", candidatos, toleranciaDias);

        assertThat(resultado)
                .as("un candidato con fecha desfasada %s dias (tolerancia %s) no debe emparejar",
                        desfase, toleranciaDias)
                .isEmpty();
    }

    // Feature: crm-anuncios-luminosos, Property 18: Conciliación bancaria completa solo con diferencia cero
    @Property(tries = 1000)
    void emparejamientoCoincideConMontoIgualEnValorAbsolutoYFechaDentroDeTolerancia(
            @ForAll("montosPositivos") BigDecimal magnitud,
            @ForAll @IntRange(min = 0, max = 10) int toleranciaDias,
            @ForAll boolean esRetiro) {
        // Movimiento con signo (deposito o retiro) de la MISMA magnitud que el candidato,
        // fecha dentro de la tolerancia y misma referencia: debe emparejar.
        BigDecimal montoMovimiento = esRetiro ? magnitud.negate() : magnitud;
        LocalDate fechaCandidato = FECHA_BASE.plusDays(toleranciaDias);
        java.util.UUID idCandidato = java.util.UUID.randomUUID();
        List<CandidatoConciliacion> candidatos = new ArrayList<>();
        candidatos.add(CandidatoConciliacion.dePoliza(idCandidato, magnitud, fechaCandidato, "REF-1"));

        Optional<CandidatoConciliacion> resultado = ReglasConciliacion.emparejar(
                montoMovimiento, FECHA_BASE, "REF-1", candidatos, toleranciaDias);

        assertThat(resultado)
                .as("un movimiento de magnitud %s (retiro=%s) debe emparejar un candidato de igual "
                        + "magnitud dentro de la tolerancia", magnitud, esRetiro)
                .isPresent();
        assertThat(resultado.get().id())
                .as("el candidato emparejado es el esperado")
                .isEqualTo(idCandidato);
    }
}
