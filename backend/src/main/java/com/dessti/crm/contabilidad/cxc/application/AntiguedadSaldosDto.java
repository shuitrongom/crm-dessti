package com.dessti.crm.contabilidad.cxc.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO de salida de la <strong>antiguedad de saldos</strong> (aging) de las Cuentas
 * Por Cobrar del tenant, clasificadas por Cliente y por rango de dias de
 * vencimiento (Req 36.5). Es una vista de solo lectura: no muta ninguna CxC.
 *
 * <p>Los rangos estandar son {@code 0-30}, {@code 31-60}, {@code 61-90} y
 * {@code >90} dias, medidos con el {@link java.time.Clock} inyectado desde la fecha
 * de vencimiento (o la fecha de emision cuando no hay vencimiento). Solo se
 * consideran las CxC con saldo pendiente ({@code pendiente} o {@code parcial}).</p>
 *
 * @param clientes lista de renglones de aging, uno por Cliente con saldo pendiente.
 */
public record AntiguedadSaldosDto(List<RenglonCliente> clientes) {

    /**
     * Renglon de aging de un Cliente: el saldo pendiente total y su desglose por
     * rango de dias de vencimiento (Req 36.5).
     *
     * @param clienteId         Cliente.
     * @param saldoTotal        saldo pendiente total del Cliente (suma de los rangos).
     * @param rango0a30         saldo con 0 a 30 dias de antiguedad.
     * @param rango31a60        saldo con 31 a 60 dias de antiguedad.
     * @param rango61a90        saldo con 61 a 90 dias de antiguedad.
     * @param rangoMas90        saldo con mas de 90 dias de antiguedad.
     */
    public record RenglonCliente(
            UUID clienteId,
            BigDecimal saldoTotal,
            BigDecimal rango0a30,
            BigDecimal rango31a60,
            BigDecimal rango61a90,
            BigDecimal rangoMas90) {
    }
}
