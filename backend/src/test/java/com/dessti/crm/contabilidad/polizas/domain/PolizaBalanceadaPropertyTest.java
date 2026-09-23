package com.dessti.crm.contabilidad.polizas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 16: Poliza contable
 * balanceada</strong> (Req 38.2, 38.3, 38.4).
 *
 * <p>Ejercita directamente la regla de negocio PURA
 * {@link PolizaContable#crear(LocalDate, TipoPoliza, String, String, UUID, List, String)}
 * sobre la entidad de dominio, sin base de datos ni contexto de Spring: la fabrica es
 * determinista y sin efectos de framework, por lo que la propiedad de balance se
 * comprueba universalmente sobre entradas arbitrarias.</p>
 *
 * <h2>Invariantes verificados (Property 16)</h2>
 * <ol>
 *   <li><strong>Poliza balanceada:</strong> cuando la suma de cargos es igual a la
 *       suma de abonos, la poliza se crea con {@code total_cargos == total_abonos}
 *       (Req 38.2, 38.3).</li>
 *   <li><strong>Poliza desbalanceada:</strong> cuando la suma de cargos difiere de la
 *       suma de abonos, la creacion se rechaza con {@link ReglaNegocioException}
 *       informando la diferencia y no construye la poliza (Req 38.4).</li>
 *   <li><strong>Reverso balanceado:</strong> el reverso de una poliza balanceada es,
 *       por construccion, tambien balanceado (Req 38.5).</li>
 * </ol>
 *
 * <h2>Convenciones numericas (espejo de produccion)</h2>
 * <p>Importes a escala {@value PolizaContable#ESCALA_MONETARIA} con redondeo
 * {@code HALF_UP}, coherentes con {@code NUMERIC(18,2)} de V33. Los generadores
 * construyen importes a partir de enteros escalados (centavos) para mantener
 * comparaciones exactas por {@code compareTo}.</p>
 */
class PolizaBalanceadaPropertyTest {

    /** Escala monetaria espejo de produccion. */
    private static final int ESCALA_MONETARIA = PolizaContable.ESCALA_MONETARIA; // 2

    /** Actor ficticio para las fabricas/mutadores de dominio. */
    private static final String ACTOR = "prueba";

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Total (importe) balanceado a repartir, en centavos de 2 a 1_000_000_000. */
    @Provide
    Arbitrary<Long> totalCentavos() {
        return Arbitraries.longs().between(2L, 1_000_000_000L);
    }

    /** Numero de partes (2..5) en las que se reparte cada lado (cargos o abonos). */
    @Provide
    Arbitrary<Integer> numeroPartes() {
        return Arbitraries.integers().between(1, 5);
    }

    /** Diferencia (excedente) estrictamente positiva en centavos, para desbalancear. */
    @Provide
    Arbitrary<Long> diferenciaCentavos() {
        return Arbitraries.longs().between(1L, 1_000_000_000L);
    }

    /**
     * Reparte {@code totalCentavos} en importes estrictamente positivos que suman
     * <em>exactamente</em> el total (el ultimo absorbe el remanente), a escala 2. El
     * numero efectivo de partes se acota a {@code min(partes, totalCentavos)} para
     * garantizar que cada importe sea &gt;= 1 centavo (los renglones no admiten
     * importes no positivos).
     */
    private static List<BigDecimal> repartir(long totalCentavos, int partes) {
        int partesEfectivas = (int) Math.max(1L, Math.min((long) partes, totalCentavos));
        List<BigDecimal> importes = new ArrayList<>();
        long base = totalCentavos / partesEfectivas;
        long acumulado = 0L;
        for (int i = 0; i < partesEfectivas; i++) {
            long parte = (i < partesEfectivas - 1) ? base : (totalCentavos - acumulado);
            acumulado += parte;
            importes.add(new BigDecimal(parte).movePointLeft(ESCALA_MONETARIA));
        }
        return importes;
    }

    private static BigDecimal centavos(long c) {
        return new BigDecimal(c).movePointLeft(ESCALA_MONETARIA);
    }

    // ----------------------------------------------------------------------
    // Property 16 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 16: Póliza contable balanceada
    @Property(tries = 1000)
    void polizaBalanceadaSeCreaConTotalesIguales(
            @ForAll("totalCentavos") long totalCentavos,
            @ForAll("numeroPartes") int partesCargo,
            @ForAll("numeroPartes") int partesAbono) {

        // Cargos y abonos suman EXACTAMENTE el mismo total (balanceada), repartidos en
        // distinto numero de renglones a cada lado.
        List<MovimientoPoliza> renglones = new ArrayList<>();
        for (BigDecimal importe : repartir(totalCentavos, partesCargo)) {
            renglones.add(MovimientoPoliza.cargo(UUID.randomUUID(), importe, ACTOR));
        }
        for (BigDecimal importe : repartir(totalCentavos, partesAbono)) {
            renglones.add(MovimientoPoliza.abono(UUID.randomUUID(), importe, ACTOR));
        }

        PolizaContable poliza = PolizaContable.crear(
                LocalDate.of(2024, 1, 15), TipoPoliza.DIARIO, "Poliza de prueba",
                "prueba", null, renglones, ACTOR);

        BigDecimal totalEsperado = centavos(totalCentavos);
        assertThat(poliza.getTotalCargos())
                .as("total de cargos == total repartido")
                .isEqualByComparingTo(totalEsperado);
        assertThat(poliza.getTotalAbonos())
                .as("total de abonos == total repartido")
                .isEqualByComparingTo(totalEsperado);
        assertThat(poliza.getTotalCargos())
                .as("poliza balanceada: total_cargos == total_abonos")
                .isEqualByComparingTo(poliza.getTotalAbonos());
    }

    // Feature: crm-anuncios-luminosos, Property 16: Póliza contable balanceada
    @Property(tries = 1000)
    void polizaDesbalanceadaSeRechazaInformandoLaDiferencia(
            @ForAll("totalCentavos") long totalCentavos,
            @ForAll("diferenciaCentavos") long diferenciaCentavos) {

        // Un cargo por total + diferencia; un abono por total -> desbalanceada.
        BigDecimal cargoImporte = centavos(totalCentavos + diferenciaCentavos);
        BigDecimal abonoImporte = centavos(totalCentavos);
        List<MovimientoPoliza> renglones = List.of(
                MovimientoPoliza.cargo(UUID.randomUUID(), cargoImporte, ACTOR),
                MovimientoPoliza.abono(UUID.randomUUID(), abonoImporte, ACTOR));

        assertThatThrownBy(() -> PolizaContable.crear(
                LocalDate.of(2024, 1, 15), TipoPoliza.DIARIO, "Poliza desbalanceada",
                "prueba", null, renglones, ACTOR))
                .as("una poliza con cargos != abonos debe rechazarse")
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no esta balanceada")
                .hasMessageContaining(centavos(diferenciaCentavos).toPlainString());
    }

    // Feature: crm-anuncios-luminosos, Property 16: Póliza contable balanceada
    @Property(tries = 1000)
    void reversoDeUnaPolizaBalanceadaTambienEstaBalanceado(
            @ForAll("totalCentavos") long totalCentavos,
            @ForAll("numeroPartes") int partesCargo,
            @ForAll("numeroPartes") int partesAbono) {

        List<MovimientoPoliza> renglones = new ArrayList<>();
        for (BigDecimal importe : repartir(totalCentavos, partesCargo)) {
            renglones.add(MovimientoPoliza.cargo(UUID.randomUUID(), importe, ACTOR));
        }
        for (BigDecimal importe : repartir(totalCentavos, partesAbono)) {
            renglones.add(MovimientoPoliza.abono(UUID.randomUUID(), importe, ACTOR));
        }
        PolizaContable original = PolizaContable.crear(
                LocalDate.of(2024, 1, 15), TipoPoliza.INGRESO, "Poliza a reversar",
                "prueba", null, renglones, ACTOR);

        PolizaContable reverso = original.reversar(LocalDate.of(2024, 2, 1), ACTOR);

        // El reverso intercambia cargos y abonos: sigue balanceado y con los totales
        // originales (que ya eran iguales entre si).
        assertThat(reverso.getTotalCargos())
                .as("el reverso esta balanceado: total_cargos == total_abonos")
                .isEqualByComparingTo(reverso.getTotalAbonos());
        assertThat(reverso.getTotalCargos())
                .as("el reverso conserva el importe total de la poliza original")
                .isEqualByComparingTo(original.getTotalAbonos());
        assertThat(reverso.getPolizaRevertidaId())
                .as("el reverso referencia la poliza revertida (Req 38.5)")
                .isEqualTo(original.getId());
        assertThat(reverso.getOrigen())
                .as("el reverso lleva origen 'reverso'")
                .isEqualTo(PolizaContable.ORIGEN_REVERSO);
    }
}
