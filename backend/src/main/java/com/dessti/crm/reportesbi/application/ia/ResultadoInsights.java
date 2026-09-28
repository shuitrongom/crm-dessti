package com.dessti.crm.reportesbi.application.ia;

import java.util.List;

/**
 * Resultado de la generacion de insights ejecutivos (Req 48, suite BI+IA). Es un
 * <em>record</em> inmutable con el narrativo en espanol de Mexico producido a partir del
 * consolidado: un resumen ejecutivo, una lista de hallazgos puntuales y la
 * <strong>procedencia</strong> del contenido.
 *
 * <h2>Procedencia y degradacion gracil</h2>
 * <p>La bandera {@link #generadoPorIa()} distingue si el narrativo lo produjo el
 * proveedor de IA configurado ({@code true}) o el generador heuristico determinista de
 * respaldo ({@code false}). Cuando la IA no esta configurada, falla o agota el tiempo,
 * el servicio degrada de forma gracil al heuristico y marca {@code generadoPorIa=false},
 * de modo que la funcion nunca rompe el flujo del tablero.</p>
 *
 * @param resumenEjecutivo narrativo breve del desempeno del periodo en es-MX; nunca
 *                         {@code null} ni vacio.
 * @param hallazgos        puntos destacados (subidas, caidas, alertas); nunca
 *                         {@code null} (puede estar vacia).
 * @param generadoPorIa    {@code true} si lo genero el proveedor de IA; {@code false} si
 *                         provino del heuristico de respaldo.
 */
public record ResultadoInsights(
        String resumenEjecutivo,
        List<String> hallazgos,
        boolean generadoPorIa) {

    /**
     * Normaliza y valida los campos, garantizando invariantes para el consumidor.
     *
     * @throws IllegalArgumentException si {@code resumenEjecutivo} es nulo o en blanco.
     */
    public ResultadoInsights {
        if (resumenEjecutivo == null || resumenEjecutivo.isBlank()) {
            throw new IllegalArgumentException("El resumen ejecutivo es obligatorio.");
        }
        hallazgos = (hallazgos == null) ? List.of() : List.copyOf(hallazgos);
    }

    /**
     * Crea un resultado producido por el proveedor de IA.
     *
     * @param resumenEjecutivo narrativo en es-MX.
     * @param hallazgos        hallazgos puntuales.
     * @return el resultado con {@code generadoPorIa=true}.
     */
    public static ResultadoInsights deIa(String resumenEjecutivo, List<String> hallazgos) {
        return new ResultadoInsights(resumenEjecutivo, hallazgos, true);
    }

    /**
     * Crea un resultado producido por el heuristico de respaldo (degradacion gracil).
     *
     * @param resumenEjecutivo narrativo en es-MX.
     * @param hallazgos        hallazgos puntuales.
     * @return el resultado con {@code generadoPorIa=false}.
     */
    public static ResultadoInsights deHeuristico(String resumenEjecutivo, List<String> hallazgos) {
        return new ResultadoInsights(resumenEjecutivo, hallazgos, false);
    }
}
