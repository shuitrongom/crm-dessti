package com.dessti.crm.contabilidad.reportes.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.dessti.crm.contabilidad.polizas.domain.TipoCuentaContable;

/**
 * Componente de dominio <strong>PURO</strong> que deriva los estados financieros de
 * un periodo a partir de un conjunto de {@link SaldoCuenta saldos por cuenta}
 * (Req 47.1, 47.3). No tiene estado, no depende de la base de datos ni del framework
 * y sus metodos son <strong>estaticos y deterministas</strong>, de modo que sus
 * invariantes —en particular la ecuacion contable del balance general
 * (<strong>Property 17</strong>)— se verifican universalmente sobre entradas
 * arbitrarias.
 *
 * <h2>Derivacion desde Polizas_Contables balanceadas</h2>
 * <p>Los saldos de entrada provienen de agregar los renglones
 * ({@code movimiento_poliza}) de las Polizas_Contables del periodo por cuenta. Como
 * cada Poliza_Contable esta balanceada ({@code suma(cargos) == suma(abonos)},
 * Property 16, Req 38.3), la suma global de {@code (cargos - abonos)} sobre todas las
 * cuentas es cero. De ahi se sigue el algebra de la ecuacion contable descrita en
 * {@link #balanceGeneral(Collection)}.</p>
 *
 * <h2>Convencion del resultado del ejercicio (Property 17, Req 47.3)</h2>
 * <p>El resultado del ejercicio ({@code ingresos - gastos}) se integra dentro del
 * capital del {@link BalanceGeneral} para que la ecuacion {@code activo = pasivo +
 * capital} sea estricta. Ver {@link BalanceGeneral#capital()}.</p>
 */
public final class EstadosFinancieros {

    /** Escala monetaria coherente con NUMERIC(18,2). */
    public static final int ESCALA_MONETARIA = 2;

    private EstadosFinancieros() {
        // Componente de utilidades de dominio: no instanciable.
    }

    /**
     * Deriva el <strong>balance general</strong> del periodo a partir de los saldos
     * por cuenta (Req 47.1, 47.3; <strong>Property 17</strong>).
     *
     * <p>Clasifica los saldos por {@link TipoCuentaContable tipo} y suma:</p>
     * <ul>
     *   <li>{@code activo}  = suma de saldos de cuentas de tipo activo.</li>
     *   <li>{@code pasivo}  = suma de saldos de cuentas de tipo pasivo.</li>
     *   <li>{@code capitalBase} = suma de saldos de cuentas de tipo capital.</li>
     *   <li>{@code resultadoEjercicio = ingresos - gastos}, integrado en el capital.</li>
     * </ul>
     *
     * <h3>Ecuacion contable (por que se cumple)</h3>
     * <p>Con la convencion de signo natural de {@link SaldoCuenta#saldo()} (deudora:
     * {@code cargos - abonos}; acreedora: {@code abonos - cargos}) y el balance global
     * de las polizas {@code sum(cargos) == sum(abonos)} se obtiene, sumando
     * {@code (cargos - abonos)} sobre todas las cuentas:</p>
     * <pre>
     *   activo + gastos - pasivo - capitalBase - ingresos = 0
     *   =&gt; activo = pasivo + capitalBase + (ingresos - gastos)
     *   =&gt; activo = pasivo + capital           (capital = capitalBase + resultado)
     * </pre>
     * <p>Por tanto {@code activo == pasivo + capital} de forma exacta (Property 17).</p>
     *
     * @param saldos saldos por cuenta del periodo; {@code null} se trata como vacio.
     * @return el {@link BalanceGeneral} del periodo; cumple {@code activo == pasivo + capital}.
     */
    public static BalanceGeneral balanceGeneral(Collection<SaldoCuenta> saldos) {
        BigDecimal activo = cero();
        BigDecimal pasivo = cero();
        BigDecimal capital = cero();
        BigDecimal ingresos = cero();
        BigDecimal gastos = cero();

        for (SaldoCuenta saldoCuenta : saldosSeguros(saldos)) {
            BigDecimal saldo = saldoCuenta.saldo();
            switch (saldoCuenta.tipo()) {
                case ACTIVO -> activo = activo.add(saldo);
                case PASIVO -> pasivo = pasivo.add(saldo);
                case CAPITAL -> capital = capital.add(saldo);
                case INGRESO -> ingresos = ingresos.add(saldo);
                case GASTO -> gastos = gastos.add(saldo);
                default -> throw new IllegalStateException(
                        "Tipo de Cuenta_Contable no contemplado: " + saldoCuenta.tipo());
            }
        }

        BigDecimal resultadoEjercicio = ingresos.subtract(gastos)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return new BalanceGeneral(activo, pasivo, capital, resultadoEjercicio);
    }

    /**
     * Deriva el <strong>estado de resultados</strong> del periodo a partir de los
     * saldos por cuenta (Req 47.1): agrega los ingresos (tipo ingreso) y los gastos
     * (tipo gasto) y expone la utilidad {@code ingresos - gastos}.
     *
     * @param saldos saldos por cuenta del periodo; {@code null} se trata como vacio.
     * @return el {@link EstadoResultados} del periodo.
     */
    public static EstadoResultados estadoDeResultados(Collection<SaldoCuenta> saldos) {
        BigDecimal ingresos = cero();
        BigDecimal gastos = cero();
        for (SaldoCuenta saldoCuenta : saldosSeguros(saldos)) {
            if (saldoCuenta.tipo() == TipoCuentaContable.INGRESO) {
                ingresos = ingresos.add(saldoCuenta.saldo());
            } else if (saldoCuenta.tipo() == TipoCuentaContable.GASTO) {
                gastos = gastos.add(saldoCuenta.saldo());
            }
        }
        return new EstadoResultados(ingresos, gastos);
    }

    /**
     * Deriva la <strong>balanza de comprobacion</strong> del periodo a partir de los
     * saldos por cuenta (Req 47.1): un renglon por cuenta con sus cargos y abonos, y
     * los grandes totales. Como cada Poliza_Contable esta balanceada (Property 16),
     * la balanza cumple {@code total_cargos == total_abonos}.
     *
     * @param saldos saldos por cuenta del periodo; {@code null} se trata como vacio.
     * @return la {@link BalanzaComprobacion} del periodo.
     */
    public static BalanzaComprobacion balanzaDeComprobacion(Collection<SaldoCuenta> saldos) {
        List<BalanzaComprobacion.Renglon> renglones = new ArrayList<>();
        BigDecimal totalCargos = cero();
        BigDecimal totalAbonos = cero();
        for (SaldoCuenta saldoCuenta : saldosSeguros(saldos)) {
            renglones.add(new BalanzaComprobacion.Renglon(
                    saldoCuenta.cuentaId(), saldoCuenta.codigo(), saldoCuenta.nombre(),
                    saldoCuenta.cargos(), saldoCuenta.abonos()));
            totalCargos = totalCargos.add(saldoCuenta.cargos());
            totalAbonos = totalAbonos.add(saldoCuenta.abonos());
        }
        return new BalanzaComprobacion(renglones, totalCargos, totalAbonos);
    }

    private static Collection<SaldoCuenta> saldosSeguros(Collection<SaldoCuenta> saldos) {
        return (saldos == null) ? List.of() : saldos;
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}
