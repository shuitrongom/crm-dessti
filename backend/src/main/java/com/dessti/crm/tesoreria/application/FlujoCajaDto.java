package com.dessti.crm.tesoreria.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de salida de la <strong>posicion de liquidez y flujo de caja</strong> del periodo
 * (Req 43, suite Tesoreria). Es una agregacion de <strong>solo lectura</strong> sobre los
 * Movimiento_Bancario del tenant: el saldo bancario acumulado, las entradas y salidas del
 * periodo, el flujo neto, y un desglose mensual para graficar la tendencia de caja.
 *
 * <p>Sigue las mejores practicas de forecasting de tesoreria (posicion consolidada +
 * flujo por horizonte temporal). No modifica dato alguno (Req 22.2).</p>
 *
 * @param generadoEn       instante UTC en que se compuso la posicion.
 * @param desde            inicio del periodo analizado (inclusivo); {@code null} si no se filtro.
 * @param hasta            fin del periodo analizado (inclusivo); {@code null} si no se filtro.
 * @param saldoAcumulado   saldo bancario acumulado (suma de todos los movimientos con signo).
 * @param entradasPeriodo  total de entradas (depositos) del periodo (>= 0).
 * @param salidasPeriodo   total de salidas (retiros/pagos) del periodo, en valor absoluto (>= 0).
 * @param flujoNetoPeriodo entradas - salidas del periodo (puede ser negativo).
 * @param cuentasActivas   numero de cuentas bancarias activas.
 * @param partidasPorConciliar numero de movimientos pendientes de conciliacion.
 * @param meses            desglose mensual del flujo (para la grafica).
 */
public record FlujoCajaDto(
        Instant generadoEn,
        LocalDate desde,
        LocalDate hasta,
        BigDecimal saldoAcumulado,
        BigDecimal entradasPeriodo,
        BigDecimal salidasPeriodo,
        BigDecimal flujoNetoPeriodo,
        long cuentasActivas,
        long partidasPorConciliar,
        List<FlujoMensualDto> meses) {

    /**
     * Punto mensual del flujo de caja.
     *
     * @param periodo  etiqueta del mes 'AAAA-MM'.
     * @param entradas entradas del mes (>= 0).
     * @param salidas  salidas del mes en valor absoluto (>= 0).
     * @param neto     entradas - salidas del mes (puede ser negativo).
     */
    public record FlujoMensualDto(
            String periodo,
            BigDecimal entradas,
            BigDecimal salidas,
            BigDecimal neto) {
    }
}
