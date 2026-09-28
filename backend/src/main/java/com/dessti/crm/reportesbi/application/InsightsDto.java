package com.dessti.crm.reportesbi.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de salida de los <strong>insights ejecutivos</strong> de la Inteligencia de
 * Negocio (Req 48, suite BI+IA). Acompana al consolidado con un narrativo en lenguaje
 * natural (es-MX): un resumen ejecutivo, una lista de hallazgos y la procedencia del
 * contenido.
 *
 * <p>La bandera {@link #generadoPorIa()} permite a la interfaz distinguir cuando el texto
 * lo produjo el proveedor de IA de cuando provino del generador heuristico de respaldo
 * (degradacion gracil), para mostrarlo con transparencia al usuario.</p>
 *
 * @param generadoEn       instante UTC en que se compusieron los insights.
 * @param desde            inicio del periodo analizado (inclusivo); {@code null} si no se filtro.
 * @param hasta            fin del periodo analizado (inclusivo); {@code null} si no se filtro.
 * @param area             etiqueta del area filtrada; {@code null} si abarca todas las areas.
 * @param resumenEjecutivo narrativo breve del desempeno del periodo en es-MX.
 * @param hallazgos        puntos destacados; puede estar vacia.
 * @param generadoPorIa    {@code true} si lo genero el proveedor de IA; {@code false} si
 *                         provino del heuristico de respaldo.
 */
public record InsightsDto(
        Instant generadoEn,
        LocalDate desde,
        LocalDate hasta,
        String area,
        String resumenEjecutivo,
        List<String> hallazgos,
        boolean generadoPorIa) {
}
