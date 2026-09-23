package com.dessti.crm.estrategia.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.estrategia.domain.ObjetivoEstrategico;
import com.dessti.crm.estrategia.domain.ResultadoClave;

/**
 * DTO de salida de un {@link ObjetivoEstrategico} (Req 58), distinto de la entidad
 * de persistencia. Incluye el {@code avance} (porcentaje ponderado de sus
 * resultados clave, Req 58.8) y el {@code estadoDerivado} (en_riesgo / en_curso /
 * cumplido) calculado en tiempo de consulta a partir del avance y del periodo
 * (Req 58.10), asi como la lista de sus resultados clave.
 *
 * @param id             identificador del objetivo.
 * @param nombre         nombre del objetivo (Req 58.2).
 * @param responsable    responsable del objetivo (Req 58.2, 58.6).
 * @param periodoInicio  inicio del periodo (Req 58.2, 58.6).
 * @param periodoFin     fin del periodo (Req 58.2).
 * @param meta           meta medible (Req 58.2).
 * @param avance         porcentaje de avance en [0, 100] (Req 58.8, 58.9).
 * @param estadoDerivado estado derivado (en_riesgo / en_curso / cumplido) (Req 58.10).
 * @param resultadosClave resultados clave ponderados del objetivo (Req 58.8).
 * @param version        version para concurrencia optimista (Req 49).
 * @param createdAt      instante de alta (UTC).
 * @param updatedAt      instante de la ultima modificacion (UTC).
 */
public record ObjetivoEstrategicoDto(
        UUID id,
        String nombre,
        String responsable,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        String meta,
        BigDecimal avance,
        String estadoDerivado,
        List<ResultadoClaveDto> resultadosClave,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ObjetivoEstrategico} a su DTO de salida,
     * calculando el estado derivado a la fecha {@code hoy} (Req 58.10).
     *
     * @param objetivo entidad a proyectar.
     * @param hoy      fecha de consulta para derivar el estado (Req 58.10).
     * @return el DTO correspondiente.
     */
    public static ObjetivoEstrategicoDto de(ObjetivoEstrategico objetivo, LocalDate hoy) {
        List<ResultadoClaveDto> resultados = objetivo.getResultadosClave().stream()
                .map(ResultadoClaveDto::de)
                .toList();
        return new ObjetivoEstrategicoDto(
                objetivo.getId(),
                objetivo.getNombre(),
                objetivo.getResponsable(),
                objetivo.getPeriodoInicio(),
                objetivo.getPeriodoFin(),
                objetivo.getMeta(),
                objetivo.getAvance(),
                objetivo.estadoDerivado(hoy).valorBd(),
                resultados,
                objetivo.getVersion(),
                objetivo.getCreatedAt(),
                objetivo.getUpdatedAt());
    }
}
