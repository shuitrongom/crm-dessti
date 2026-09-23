package com.dessti.crm.contabilidad.reportes.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.IngresosPeriodoProjection;

/**
 * DTO de salida del reporte de <strong>ingresos por periodo</strong> (Req 39.1): los
 * importes fiscales acumulados de las Facturas timbradas del periodo, opcionalmente
 * acotados a un Cliente (Req 39.3). Es una agregacion de solo lectura (Req 39.2).
 *
 * @param desde          inicio del periodo (inclusivo).
 * @param hasta          fin del periodo (inclusivo).
 * @param clienteId      Cliente al que se acoto; {@code null} si incluye a todos.
 * @param numeroFacturas numero de Facturas timbradas consideradas.
 * @param subtotal       suma de los subtotales (ingresos gravables) del periodo.
 * @param iva            suma del IVA trasladado del periodo.
 * @param retenciones    suma de las retenciones del periodo.
 * @param total          suma de los totales facturados del periodo.
 */
public record IngresosPeriodoDto(
        LocalDate desde,
        LocalDate hasta,
        UUID clienteId,
        long numeroFacturas,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal retenciones,
        BigDecimal total) {

    /** Escala monetaria coherente con NUMERIC(18,2). */
    private static final int ESCALA_MONETARIA = 2;

    /**
     * Proyecta la agregacion fiscal a su DTO de <em>ingresos por periodo</em>,
     * normalizando los importes a escala 2 (HALF_UP).
     *
     * @param proyeccion agregacion de importes fiscales del periodo.
     * @param desde      inicio del periodo (inclusivo).
     * @param hasta      fin del periodo (inclusivo).
     * @param clienteId  Cliente al que se acoto; {@code null} si incluye a todos.
     * @return el DTO de ingresos por periodo.
     */
    public static IngresosPeriodoDto de(IngresosPeriodoProjection proyeccion,
                                        LocalDate desde, LocalDate hasta, UUID clienteId) {
        return new IngresosPeriodoDto(
                desde,
                hasta,
                clienteId,
                proyeccion.getNumeroFacturas(),
                normalizar(proyeccion.getSubtotal()),
                normalizar(proyeccion.getIva()),
                normalizar(proyeccion.getRetenciones()),
                normalizar(proyeccion.getTotal()));
    }

    private static BigDecimal normalizar(BigDecimal importe) {
        BigDecimal valor = (importe == null) ? BigDecimal.ZERO : importe;
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}
