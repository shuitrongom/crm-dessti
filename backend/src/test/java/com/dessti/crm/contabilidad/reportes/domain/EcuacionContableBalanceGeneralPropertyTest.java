package com.dessti.crm.contabilidad.reportes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.contabilidad.polizas.domain.MovimientoPoliza;
import com.dessti.crm.contabilidad.polizas.domain.NaturalezaCuenta;
import com.dessti.crm.contabilidad.polizas.domain.PolizaContable;
import com.dessti.crm.contabilidad.polizas.domain.TipoCuentaContable;
import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 17: Ecuacion contable
 * del balance general</strong> (Req 47.3).
 *
 * <p>Ejercita el componente de dominio <strong>PURO</strong>
 * {@link EstadosFinancieros}, sin base de datos ni contexto de Spring: sus funciones
 * son deterministas y sin efectos de framework, por lo que la ecuacion contable se
 * comprueba universalmente sobre conjuntos de Polizas_Contables balanceadas generados
 * arbitrariamente.</p>
 *
 * <h2>Estrategia de generacion</h2>
 * <p>Para cada caso se genera un conjunto de renglones de una Poliza_Contable
 * <strong>balanceada</strong> (suma de cargos == suma de abonos, garantizado por la
 * fabrica {@link PolizaContable#crear}): el total se reparte en cargos y en abonos, y
 * cada renglon se asigna a una de varias Cuentas_Contables con un
 * {@link TipoCuentaContable tipo} y una {@link NaturalezaCuenta naturaleza}
 * arbitrarios. Los renglones se agregan por cuenta en {@link SaldoCuenta}, se deriva
 * el {@link BalanceGeneral} con {@link EstadosFinancieros#balanceGeneral} y se
 * verifica la ecuacion contable.</p>
 *
 * <h2>Invariantes verificados (Property 17)</h2>
 * <ol>
 *   <li><strong>Ecuacion contable (Req 47.3):</strong> {@code activo == pasivo +
 *       capital}, con el resultado del ejercicio {@code (ingresos - gastos)}
 *       integrado en el capital ({@link BalanceGeneral#capital()}). Como el conjunto
 *       esta balanceado por construccion, la igualdad es exacta.</li>
 *   <li><strong>Coherencia con el estado de resultados:</strong> el resultado del
 *       ejercicio del balance coincide con la utilidad del
 *       {@link EstadoResultados}.</li>
 *   <li><strong>Balanza cuadrada:</strong> en la balanza de comprobacion derivada,
 *       {@code total_cargos == total_abonos}.</li>
 * </ol>
 *
 * <h2>Convenciones numericas (espejo de produccion)</h2>
 * <p>Importes a escala {@value SaldoCuenta#ESCALA_MONETARIA} con redondeo
 * {@code HALF_UP}. Los generadores construyen importes a partir de enteros escalados
 * (centavos) para mantener comparaciones exactas por {@code compareTo}.</p>
 */
class EcuacionContableBalanceGeneralPropertyTest {

    /** Escala monetaria espejo de produccion. */
    private static final int ESCALA_MONETARIA = SaldoCuenta.ESCALA_MONETARIA; // 2

    /** Actor ficticio para las fabricas de dominio. */
    private static final String ACTOR = "prueba";

    /** Todos los tipos contables posibles (activo, pasivo, capital, ingreso, gasto). */
    private static final TipoCuentaContable[] TIPOS = TipoCuentaContable.values();

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Total (importe) balanceado a repartir, en centavos de 2 a 1_000_000_000. */
    @Provide
    Arbitrary<Long> totalCentavos() {
        return Arbitraries.longs().between(2L, 1_000_000_000L);
    }

    /** Numero de partes (1..6) en las que se reparte cada lado (cargos o abonos). */
    @Provide
    Arbitrary<Integer> numeroPartes() {
        return Arbitraries.integers().between(1, 6);
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
            importes.add(centavos(parte));
        }
        return importes;
    }

    private static BigDecimal centavos(long c) {
        return new BigDecimal(c).movePointLeft(ESCALA_MONETARIA);
    }

    /**
     * Construye la lista de {@link SaldoCuenta} agregando los renglones de una
     * Poliza_Contable balanceada por cuenta. Cada renglon se reparte deterministamente
     * entre {@code numeroCuentas} cuentas (por indice), y a cada cuenta se le asigna un
     * tipo derivado de {@code semillaTipos} para cubrir tipos arbitrarios de forma
     * reproducible. La naturaleza se deriva del tipo con la convencion contable
     * estandar (activo/gasto deudora; pasivo/capital/ingreso acreedora).
     */
    private static List<SaldoCuenta> agregarSaldos(PolizaContable poliza, int numeroCuentas,
                                                   int semillaTipos) {
        int cuentas = Math.max(1, numeroCuentas);
        UUID[] ids = new UUID[cuentas];
        TipoCuentaContable[] tipos = new TipoCuentaContable[cuentas];
        BigDecimal[] cargos = new BigDecimal[cuentas];
        BigDecimal[] abonos = new BigDecimal[cuentas];
        BigDecimal cero = BigDecimal.ZERO.setScale(ESCALA_MONETARIA);
        for (int i = 0; i < cuentas; i++) {
            ids[i] = UUID.randomUUID();
            tipos[i] = TIPOS[Math.floorMod(semillaTipos + i, TIPOS.length)];
            cargos[i] = cero;
            abonos[i] = cero;
        }

        int indice = 0;
        for (MovimientoPoliza renglon : poliza.getRenglones()) {
            int cuenta = Math.floorMod(indice, cuentas);
            cargos[cuenta] = cargos[cuenta].add(renglon.getCargo());
            abonos[cuenta] = abonos[cuenta].add(renglon.getAbono());
            indice++;
        }

        List<SaldoCuenta> saldos = new ArrayList<>();
        for (int i = 0; i < cuentas; i++) {
            NaturalezaCuenta naturaleza = naturalezaDe(tipos[i]);
            saldos.add(SaldoCuenta.de(
                    ids[i], "C" + i, "Cuenta " + i, tipos[i], naturaleza, cargos[i], abonos[i]));
        }
        return saldos;
    }

    /** Naturaleza contable estandar del tipo: activo/gasto deudora; resto acreedora. */
    private static NaturalezaCuenta naturalezaDe(TipoCuentaContable tipo) {
        return switch (tipo) {
            case ACTIVO, GASTO -> NaturalezaCuenta.DEUDORA;
            case PASIVO, CAPITAL, INGRESO -> NaturalezaCuenta.ACREEDORA;
        };
    }

    /**
     * Construye una Poliza_Contable balanceada repartiendo {@code totalCentavos} en
     * cargos y en abonos (dos reparticiones independientes del mismo total).
     */
    private static PolizaContable polizaBalanceada(long totalCentavos, int partesCargo,
                                                   int partesAbono) {
        List<MovimientoPoliza> renglones = new ArrayList<>();
        for (BigDecimal importe : repartir(totalCentavos, partesCargo)) {
            renglones.add(MovimientoPoliza.cargo(UUID.randomUUID(), importe, ACTOR));
        }
        for (BigDecimal importe : repartir(totalCentavos, partesAbono)) {
            renglones.add(MovimientoPoliza.abono(UUID.randomUUID(), importe, ACTOR));
        }
        return PolizaContable.crear(
                java.time.LocalDate.of(2024, 6, 15), TipoPoliza.DIARIO,
                "Poliza balanceada de prueba", "prueba", null, renglones, ACTOR);
    }

    // ----------------------------------------------------------------------
    // Property 17 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 17: Ecuación contable del balance general
    @Property(tries = 1000)
    void balanceGeneralCumpleLaEcuacionContable(
            @ForAll("totalCentavos") long totalCentavos,
            @ForAll("numeroPartes") int partesCargo,
            @ForAll("numeroPartes") int partesAbono,
            @ForAll @IntRange(min = 1, max = 8) int numeroCuentas,
            @ForAll @IntRange(min = 0, max = 4) int semillaTipos) {

        PolizaContable poliza = polizaBalanceada(totalCentavos, partesCargo, partesAbono);
        List<SaldoCuenta> saldos = agregarSaldos(poliza, numeroCuentas, semillaTipos);

        BalanceGeneral balance = EstadosFinancieros.balanceGeneral(saldos);

        assertThat(balance.activo())
                .as("ecuacion contable: activo == pasivo + capital (Property 17)")
                .isEqualByComparingTo(balance.pasivo().add(balance.capital()));
        assertThat(balance.cuadra())
                .as("el balance general cuadra (activo == pasivo + capital)")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 17: Ecuación contable del balance general
    @Property(tries = 1000)
    void resultadoDelEjercicioCoincideConLaUtilidadDelEstadoDeResultados(
            @ForAll("totalCentavos") long totalCentavos,
            @ForAll("numeroPartes") int partesCargo,
            @ForAll("numeroPartes") int partesAbono,
            @ForAll @IntRange(min = 1, max = 8) int numeroCuentas,
            @ForAll @IntRange(min = 0, max = 4) int semillaTipos) {

        PolizaContable poliza = polizaBalanceada(totalCentavos, partesCargo, partesAbono);
        List<SaldoCuenta> saldos = agregarSaldos(poliza, numeroCuentas, semillaTipos);

        BalanceGeneral balance = EstadosFinancieros.balanceGeneral(saldos);
        EstadoResultados estado = EstadosFinancieros.estadoDeResultados(saldos);

        assertThat(balance.resultadoEjercicio())
                .as("el resultado del ejercicio del balance == utilidad del estado de resultados")
                .isEqualByComparingTo(estado.utilidad());
    }

    // Feature: crm-anuncios-luminosos, Property 17: Ecuación contable del balance general
    @Property(tries = 1000)
    void balanzaDeComprobacionCuadra(
            @ForAll("totalCentavos") long totalCentavos,
            @ForAll("numeroPartes") int partesCargo,
            @ForAll("numeroPartes") int partesAbono,
            @ForAll @IntRange(min = 1, max = 8) int numeroCuentas,
            @ForAll @IntRange(min = 0, max = 4) int semillaTipos) {

        PolizaContable poliza = polizaBalanceada(totalCentavos, partesCargo, partesAbono);
        List<SaldoCuenta> saldos = agregarSaldos(poliza, numeroCuentas, semillaTipos);

        BalanzaComprobacion balanza = EstadosFinancieros.balanzaDeComprobacion(saldos);

        assertThat(balanza.totalCargos())
                .as("balanza de comprobacion: total_cargos == total_abonos")
                .isEqualByComparingTo(balanza.totalAbonos());
        assertThat(balanza.cuadra())
                .as("la balanza de comprobacion cuadra")
                .isTrue();
    }
}
