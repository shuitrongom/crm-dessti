package com.dessti.crm.social.analitica.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.social.analitica.domain.MetricasSociales;

/**
 * DTO de salida estructurado de la <strong>analitica social</strong> (Req 66.1,
 * 66.4): el conjunto de metricas por Canal_Social del tenant para un periodo y unos
 * filtros dados, apto tanto para la consulta como para la exportacion (Req 66.4).
 *
 * <p>Reune los filtros aplicados (para trazabilidad de la exportacion) y la lista de
 * {@link MetricasSocialesDto} por canal. Es una agregacion de solo lectura que no
 * modifica los datos de origen (Req 66.1) y ya viene acotada al {@code tenant_id}
 * vigente (Req 66.6).</p>
 *
 * @param desde        inicio del periodo (inclusivo) aplicado; {@code null} si no se filtro.
 * @param hasta        fin del periodo (inclusivo) aplicado; {@code null} si no se filtro.
 * @param canal        Canal_Social al que se acoto ({@code whatsapp}/{@code messenger}/
 *                     {@code instagram}); {@code null} si incluye todos.
 * @param canalVentaId Canal_Venta (Req 63) al que se segmento; {@code null} si no aplica.
 * @param exportacion  {@code true} si el payload corresponde a una exportacion (Req 66.4).
 * @param canales      metricas por Canal_Social; nunca {@code null} (puede ir vacia).
 */
public record ResumenAnaliticaSocialDto(
        LocalDate desde,
        LocalDate hasta,
        String canal,
        UUID canalVentaId,
        boolean exportacion,
        List<MetricasSocialesDto> canales) {

    /**
     * Construye el resumen a partir de las metricas de dominio por canal y de los
     * filtros aplicados.
     *
     * @param metricas     metricas de dominio por canal; obligatorio (puede ir vacia).
     * @param desde        inicio del periodo aplicado; {@code null} si no se filtro.
     * @param hasta        fin del periodo aplicado; {@code null} si no se filtro.
     * @param canal        etiqueta del Canal_Social aplicado; {@code null} si todos.
     * @param canalVentaId Canal_Venta al que se segmento; {@code null} si no aplica.
     * @param exportacion  {@code true} si es una exportacion.
     * @return el DTO de resumen de analitica social.
     */
    public static ResumenAnaliticaSocialDto de(List<MetricasSociales> metricas,
                                               LocalDate desde, LocalDate hasta,
                                               String canal, UUID canalVentaId,
                                               boolean exportacion) {
        List<MetricasSocialesDto> canales = metricas.stream()
                .map(MetricasSocialesDto::de)
                .toList();
        return new ResumenAnaliticaSocialDto(desde, hasta, canal, canalVentaId, exportacion, canales);
    }
}
