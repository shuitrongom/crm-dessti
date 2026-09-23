package com.dessti.crm.contabilidad.reportes.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.dessti.crm.contabilidad.reportes.domain.EstadoResultados;

/**
 * DTO de salida del <strong>estado de resultados</strong> de un periodo (Req 47.1),
 * distinto del valor de dominio. Presenta los ingresos, los gastos y la utilidad
 * ({@code ingresos - gastos}) del periodo.
 *
 * @param desde    inicio del periodo (inclusivo); {@code null} si no se acoto.
 * @param hasta    fin del periodo (inclusivo); {@code null} si no se acoto.
 * @param ingresos suma de los saldos de las cuentas de ingreso.
 * @param gastos   suma de los saldos de las cuentas de gasto.
 * @param utilidad utilidad del periodo (ingresos - gastos); negativa si hay perdida.
 */
public record EstadoResultadosDto(
        LocalDate desde,
        LocalDate hasta,
        BigDecimal ingresos,
        BigDecimal gastos,
        BigDecimal utilidad) {

    /**
     * Proyecta un {@link EstadoResultados} de dominio a su DTO de salida, anexando el
     * periodo consultado.
     *
     * @param estado estado de resultados de dominio.
     * @param desde  inicio del periodo (inclusivo); puede ser {@code null}.
     * @param hasta  fin del periodo (inclusivo); puede ser {@code null}.
     * @return el DTO correspondiente.
     */
    public static EstadoResultadosDto de(EstadoResultados estado, LocalDate desde, LocalDate hasta) {
        return new EstadoResultadosDto(
                desde,
                hasta,
                estado.ingresos(),
                estado.gastos(),
                estado.utilidad());
    }
}
