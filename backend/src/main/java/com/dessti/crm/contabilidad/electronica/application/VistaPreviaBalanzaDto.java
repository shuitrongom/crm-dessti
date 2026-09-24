package com.dessti.crm.contabilidad.electronica.application;

import java.math.BigDecimal;

/**
 * Vista previa de la Balanza_XML antes de exportar (Req 5.1, 5.3): numero de cuentas
 * con movimiento/saldo en el periodo, totales de cargos y abonos, y si la balanza
 * cuadra (advertencia de descuadre si no).
 *
 * @param numeroCuentas numero de cuentas que se incluiran.
 * @param totalDebe     total de cargos del periodo.
 * @param totalHaber    total de abonos del periodo.
 * @param cuadra        {@code true} si {@code totalDebe == totalHaber}.
 */
public record VistaPreviaBalanzaDto(
        int numeroCuentas,
        BigDecimal totalDebe,
        BigDecimal totalHaber,
        boolean cuadra) {
}
