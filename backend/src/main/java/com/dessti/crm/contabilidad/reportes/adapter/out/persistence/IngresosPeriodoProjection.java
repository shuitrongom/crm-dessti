package com.dessti.crm.contabilidad.reportes.adapter.out.persistence;

import java.math.BigDecimal;

/**
 * Proyeccion Spring Data de solo lectura con los importes fiscales acumulados de las
 * Facturas timbradas de un periodo (Req 39.1): subtotal (ingresos), IVA trasladado,
 * retenciones y total. Sustenta los reportes de <em>ingresos por periodo</em> y de
 * <em>IVA trasladado y retenido</em> (Req 39.1), como agregacion de solo lectura que
 * no modifica los datos de origen (Req 39.2).
 */
public interface IngresosPeriodoProjection {

    /**
     * @return numero de Facturas timbradas consideradas en el periodo.
     */
    long getNumeroFacturas();

    /**
     * @return suma de los subtotales (ingresos gravables) del periodo.
     */
    BigDecimal getSubtotal();

    /**
     * @return suma del IVA trasladado del periodo.
     */
    BigDecimal getIva();

    /**
     * @return suma de las retenciones del periodo.
     */
    BigDecimal getRetenciones();

    /**
     * @return suma de los totales facturados del periodo.
     */
    BigDecimal getTotal();
}
