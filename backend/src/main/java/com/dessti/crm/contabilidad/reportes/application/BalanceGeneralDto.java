package com.dessti.crm.contabilidad.reportes.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.dessti.crm.contabilidad.reportes.domain.BalanceGeneral;

/**
 * DTO de salida del <strong>balance general</strong> de un periodo (Req 47.1, 47.3),
 * distinto del valor de dominio. Presenta el activo, el pasivo, el capital contable
 * (con el resultado del ejercicio integrado), el capital base y el resultado del
 * ejercicio por separado, y la bandera {@link #cuadra} que indica si se satisface la
 * ecuacion contable {@code activo == pasivo + capital} (Property 17).
 *
 * @param desde              inicio del periodo (inclusivo); {@code null} si no se acoto.
 * @param hasta              fin del periodo (inclusivo); {@code null} si no se acoto.
 * @param activo             suma de los saldos de las cuentas de activo.
 * @param pasivo             suma de los saldos de las cuentas de pasivo.
 * @param capital            capital contable total (incluye el resultado del ejercicio).
 * @param capitalBase        capital contable sin el resultado del ejercicio.
 * @param resultadoEjercicio resultado del ejercicio del periodo (ingresos - gastos).
 * @param cuadra             {@code true} si {@code activo == pasivo + capital} (Property 17).
 */
public record BalanceGeneralDto(
        LocalDate desde,
        LocalDate hasta,
        BigDecimal activo,
        BigDecimal pasivo,
        BigDecimal capital,
        BigDecimal capitalBase,
        BigDecimal resultadoEjercicio,
        boolean cuadra) {

    /**
     * Proyecta un {@link BalanceGeneral} de dominio a su DTO de salida, anexando el
     * periodo consultado.
     *
     * @param balance balance general de dominio.
     * @param desde   inicio del periodo (inclusivo); puede ser {@code null}.
     * @param hasta   fin del periodo (inclusivo); puede ser {@code null}.
     * @return el DTO correspondiente.
     */
    public static BalanceGeneralDto de(BalanceGeneral balance, LocalDate desde, LocalDate hasta) {
        return new BalanceGeneralDto(
                desde,
                hasta,
                balance.activo(),
                balance.pasivo(),
                balance.capital(),
                balance.capitalBase(),
                balance.resultadoEjercicio(),
                balance.cuadra());
    }
}
