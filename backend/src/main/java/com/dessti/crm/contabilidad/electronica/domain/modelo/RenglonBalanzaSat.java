package com.dessti.crm.contabilidad.electronica.domain.modelo;

import java.math.BigDecimal;

/**
 * Modelo plano de un renglon de la Balanza_XML del SAT (Anexo 24), por cuenta. Es la
 * entrada al {@code GeneradorBalanzaXml}.
 *
 * @param numCta   codigo de la cuenta contable ({@code NumCta}).
 * @param saldoIni saldo inicial del periodo ({@code SaldoIni}).
 * @param debe     total de cargos del periodo ({@code Debe}).
 * @param haber    total de abonos del periodo ({@code Haber}).
 * @param saldoFin saldo final del periodo ({@code SaldoFin}).
 */
public record RenglonBalanzaSat(
        String numCta,
        BigDecimal saldoIni,
        BigDecimal debe,
        BigDecimal haber,
        BigDecimal saldoFin) {
}
