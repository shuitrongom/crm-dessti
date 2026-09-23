package com.dessti.crm.tesoreria.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de importar un estado de cuenta bancario a traves del
 * {@link ImportacionBancariaPort} (Req 43.2). Record inmutable del contrato del
 * puerto con los saldos del periodo y sus {@link MovimientoImportado}.
 *
 * @param saldoInicial saldo inicial del periodo.
 * @param saldoFinal   saldo final del periodo (saldo bancario de la conciliacion,
 *                     Req 43.5).
 * @param movimientos  movimientos del estado de cuenta; nunca {@code null} (puede ser
 *                     vacio).
 */
public record EstadoCuentaImportado(
        BigDecimal saldoInicial,
        BigDecimal saldoFinal,
        List<MovimientoImportado> movimientos) {

    /**
     * Compacta el record garantizando que {@code movimientos} nunca sea {@code null}.
     */
    public EstadoCuentaImportado {
        movimientos = (movimientos == null) ? List.of() : List.copyOf(movimientos);
    }
}
