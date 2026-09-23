package com.dessti.crm.contabilidad.reportes.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.IngresosPeriodoProjection;

/**
 * DTO de salida del reporte de <strong>IVA trasladado y retenido por periodo</strong>
 * (Req 39.1): el IVA trasladado (16%) y las retenciones acumuladas de las Facturas
 * timbradas del periodo, opcionalmente acotados a un Cliente (Req 39.3). Es una
 * agregacion de solo lectura (Req 39.2).
 *
 * @param desde          inicio del periodo (inclusivo).
 * @param hasta          fin del periodo (inclusivo).
 * @param clienteId      Cliente al que se acoto; {@code null} si incluye a todos.
 * @param numeroFacturas numero de Facturas timbradas consideradas.
 * @param baseGravable   suma de los subtotales base del IVA (ingresos gravables).
 * @param ivaTrasladado  suma del IVA trasladado del periodo.
 * @param ivaRetenido    suma de las retenciones del periodo.
 */
public record IvaPeriodoDto(
        LocalDate desde,
        LocalDate hasta,
        UUID clienteId,
        long numeroFacturas,
        BigDecimal baseGravable,
        BigDecimal ivaTrasladado,
        BigDecimal ivaRetenido) {

    /** Escala monetaria coherente con NUMERIC(18,2). */
    private static final int ESCALA_MONETARIA = 2;

    /**
     * Proyecta la agregacion fiscal a su DTO de <em>IVA trasladado y retenido</em>,
     * normalizando los importes a escala 2 (HALF_UP).
     *
     * @param proyeccion agregacion de importes fiscales del periodo.
     * @param desde      inicio del periodo (inclusivo).
     * @param hasta      fin del periodo (inclusivo).
     * @param clienteId  Cliente al que se acoto; {@code null} si incluye a todos.
     * @return el DTO de IVA trasladado y retenido por periodo.
     */
    public static IvaPeriodoDto de(IngresosPeriodoProjection proyeccion,
                                   LocalDate desde, LocalDate hasta, UUID clienteId) {
        return new IvaPeriodoDto(
                desde,
                hasta,
                clienteId,
                proyeccion.getNumeroFacturas(),
                normalizar(proyeccion.getSubtotal()),
                normalizar(proyeccion.getIva()),
                normalizar(proyeccion.getRetenciones()));
    }

    private static BigDecimal normalizar(BigDecimal importe) {
        BigDecimal valor = (importe == null) ? BigDecimal.ZERO : importe;
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}
