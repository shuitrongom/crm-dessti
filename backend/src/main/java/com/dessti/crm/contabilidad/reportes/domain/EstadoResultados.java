package com.dessti.crm.contabilidad.reportes.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valor de dominio <strong>puro e inmutable</strong> del <strong>estado de
 * resultados</strong> de un periodo, derivado de los {@link SaldoCuenta saldos por
 * cuenta} (Req 47.1). Es el resultado de {@link EstadosFinancieros#estadoDeResultados}.
 *
 * <p>El estado de resultados agrega los ingresos y los gastos del periodo y expone la
 * <strong>utilidad</strong> (o perdida) como {@code utilidad = ingresos - gastos}.
 * Esta utilidad es exactamente el <em>resultado del ejercicio</em> que
 * {@link EstadosFinancieros#balanceGeneral} integra en el capital del balance general
 * para que se cumpla la ecuacion contable (Property 17, Req 47.3).</p>
 *
 * <p>Todos los importes se manejan a escala {@value #ESCALA_MONETARIA} con redondeo
 * {@code HALF_UP}.</p>
 *
 * @param ingresos suma de los saldos de las cuentas de tipo ingreso, escala 2.
 * @param gastos   suma de los saldos de las cuentas de tipo gasto, escala 2.
 */
public record EstadoResultados(
        BigDecimal ingresos,
        BigDecimal gastos) {

    /** Escala monetaria coherente con NUMERIC(18,2). */
    public static final int ESCALA_MONETARIA = 2;

    /**
     * Normaliza los importes a escala 2 (HALF_UP) para comparaciones exactas.
     */
    public EstadoResultados {
        ingresos = normalizar(ingresos);
        gastos = normalizar(gastos);
    }

    /**
     * Utilidad (o perdida) del periodo: {@code ingresos - gastos}. Coincide con el
     * resultado del ejercicio del balance general (Req 47.3).
     *
     * @return la utilidad del periodo, escala 2 (negativa si hay perdida).
     */
    public BigDecimal utilidad() {
        return ingresos.subtract(gastos)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizar(BigDecimal importe) {
        BigDecimal valor = (importe == null) ? BigDecimal.ZERO : importe;
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}
