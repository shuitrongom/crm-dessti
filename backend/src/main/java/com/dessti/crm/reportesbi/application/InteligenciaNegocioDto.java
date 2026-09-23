package com.dessti.crm.reportesbi.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de salida del analisis consolidado de Inteligencia de Negocio (Req 48.1). Es una
 * agregacion de <strong>solo lectura</strong> (Req 48.2) que reune los indicadores de
 * todas las areas (o del area filtrada, Req 48.4) para el periodo actual y, cuando cada
 * indicador aporta historico, su comparativo con el periodo anterior y la variacion
 * derivada (tendencias/comparativos, Req 48.1). El controlador lo serializa y es la
 * carga util de la exportacion (Req 48.4).
 *
 * @param generadoEn       instante UTC en que se compuso el consolidado.
 * @param desde            inicio del periodo actual (inclusivo); {@code null} si no se filtro.
 * @param hasta            fin del periodo actual (inclusivo); {@code null} si no se filtro.
 * @param desdeComparativo inicio del periodo anterior comparado; {@code null} si no aplica.
 * @param hastaComparativo fin del periodo anterior comparado; {@code null} si no aplica.
 * @param area             etiqueta del area filtrada (Req 48.4); {@code null} incluye todas.
 * @param dimension        dimension de analisis del filtro (Req 48.4); {@code null} si no aplica.
 * @param areas            indicadores consolidados agrupados por area (Req 48.1).
 */
public record InteligenciaNegocioDto(
        Instant generadoEn,
        LocalDate desde,
        LocalDate hasta,
        LocalDate desdeComparativo,
        LocalDate hastaComparativo,
        String area,
        String dimension,
        List<IndicadoresAreaDto> areas) {
}
