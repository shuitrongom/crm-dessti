package com.dessti.crm.contabilidad.reportes.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valor de dominio <strong>puro e inmutable</strong> del <strong>balance general</strong>
 * de un periodo, derivado de los {@link SaldoCuenta saldos por cuenta} (Req 47.1,
 * 47.3). Es el resultado de {@link EstadosFinancieros#balanceGeneral}.
 *
 * <h2>Ecuacion contable (Property 17, Req 47.3)</h2>
 * <p>El balance general cumple, por construccion, la ecuacion contable fundamental
 * <strong>{@code activo = pasivo + capital}</strong>. Para que la igualdad sea
 * estricta, el <em>resultado del ejercicio</em> del periodo
 * ({@code resultadoEjercicio = ingresos - gastos}) se <strong>integra dentro del
 * capital</strong>: {@link #capital()} devuelve el capital contable ya <em>incluido</em>
 * el resultado del ejercicio ({@code capitalBase + resultadoEjercicio}). Esta es la
 * convencion adoptada en todo el modulo: el balance se presenta con el capital total
 * (aportado + resultado del ejercicio), de modo que
 * {@code activo.compareTo(pasivo.add(capital())) == 0} siempre.</p>
 *
 * <p>El componente conserva ademas {@link #capitalBase()} (capital contable sin el
 * resultado) y {@link #resultadoEjercicio()} para presentarlos por separado en el
 * reporte. Todos los importes se manejan a escala {@value #ESCALA_MONETARIA} con
 * redondeo {@code HALF_UP}.</p>
 *
 * @param activo             suma de los saldos de las cuentas de tipo activo, escala 2.
 * @param pasivo             suma de los saldos de las cuentas de tipo pasivo, escala 2.
 * @param capitalBase        suma de los saldos de las cuentas de tipo capital (sin el
 *                           resultado del ejercicio), escala 2.
 * @param resultadoEjercicio resultado del ejercicio del periodo (ingresos - gastos),
 *                           escala 2; positivo es utilidad, negativo es perdida.
 */
public record BalanceGeneral(
        BigDecimal activo,
        BigDecimal pasivo,
        BigDecimal capitalBase,
        BigDecimal resultadoEjercicio) {

    /** Escala monetaria coherente con NUMERIC(18,2). */
    public static final int ESCALA_MONETARIA = 2;

    /**
     * Normaliza los importes a escala 2 (HALF_UP) para comparaciones exactas.
     */
    public BalanceGeneral {
        activo = normalizar(activo);
        pasivo = normalizar(pasivo);
        capitalBase = normalizar(capitalBase);
        resultadoEjercicio = normalizar(resultadoEjercicio);
    }

    /**
     * Capital contable total del balance, con el <strong>resultado del ejercicio
     * integrado</strong> ({@code capitalBase + resultadoEjercicio}). Es el termino
     * del lado derecho de la ecuacion contable {@code activo = pasivo + capital}
     * (Property 17, Req 47.3).
     *
     * @return el capital total (incluido el resultado del ejercicio), escala 2.
     */
    public BigDecimal capital() {
        return capitalBase.add(resultadoEjercicio)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Indica si el balance general <strong>cuadra</strong>: la ecuacion contable
     * {@code activo == pasivo + capital} (con el resultado del ejercicio integrado en
     * el capital) se satisface exactamente (Property 17, Req 47.3).
     *
     * @return {@code true} si {@code activo == pasivo + capital}.
     */
    public boolean cuadra() {
        return activo.compareTo(pasivo.add(capital())) == 0;
    }

    private static BigDecimal normalizar(BigDecimal importe) {
        BigDecimal valor = (importe == null) ? BigDecimal.ZERO : importe;
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}
