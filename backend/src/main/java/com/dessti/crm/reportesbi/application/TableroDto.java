package com.dessti.crm.reportesbi.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO de salida del Tablero de indicadores por area (Req 22). Es una foto de
 * <strong>solo lectura</strong> (Req 22.2) compuesta por la capa de aplicacion a partir
 * de los puertos de indicadores de cada area, para el filtro por fecha/Cliente
 * aplicado (Req 22.3). El controlador lo serializa; tambien es la carga util de la
 * exportacion (Req 22.4), que el adaptador de entrada puede volcar a un formato
 * estructurado.
 *
 * @param generadoEn instante UTC en que se compuso el Tablero.
 * @param desde      inicio del periodo del filtro (inclusivo); {@code null} si no se filtro.
 * @param hasta      fin del periodo del filtro (inclusivo); {@code null} si no se filtro.
 * @param clienteId  Cliente del filtro (Req 22.3); {@code null} si incluye a todos.
 * @param areas      indicadores agrupados por area (Req 22.1), en el orden del catalogo.
 */
public record TableroDto(
        Instant generadoEn,
        LocalDate desde,
        LocalDate hasta,
        UUID clienteId,
        List<IndicadoresAreaDto> areas) {
}
