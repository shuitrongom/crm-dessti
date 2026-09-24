package com.dessti.crm.contabilidad.electronica.domain.modelo;

import java.math.BigDecimal;

/**
 * Modelo plano de un renglon (transaccion) de una poliza para el Polizas_XML del
 * SAT (Anexo 24).
 *
 * @param numCta   codigo de la cuenta contable ({@code NumCta}).
 * @param desCta   nombre/descripcion de la cuenta ({@code DesCta}).
 * @param concepto concepto del renglon ({@code Concepto}).
 * @param debe     importe cargado ({@code Debe}); cero si es un abono.
 * @param haber    importe abonado ({@code Haber}); cero si es un cargo.
 */
public record TransaccionSat(
        String numCta,
        String desCta,
        String concepto,
        BigDecimal debe,
        BigDecimal haber) {
}
