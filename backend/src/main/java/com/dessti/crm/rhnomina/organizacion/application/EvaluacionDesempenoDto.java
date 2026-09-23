package com.dessti.crm.rhnomina.organizacion.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.rhnomina.organizacion.domain.EvaluacionDesempeno;

/**
 * DTO de salida de una {@link EvaluacionDesempeno} (Req 12.2, 61.3, 61.8),
 * distinto de la entidad de persistencia.
 *
 * @param id           identificador de la evaluacion.
 * @param empleadoId   Empleado evaluado.
 * @param periodo      codigo del periodo (AAAA-MM).
 * @param calificacion calificacion dentro de la escala 1.00..5.00.
 * @param comentarios  comentarios; puede ser {@code null}.
 * @param evaluadaEn   instante en que se registro la evaluacion (UTC).
 * @param version      version para concurrencia optimista (Req 49).
 * @param createdAt    instante de alta (UTC).
 * @param updatedAt    instante de la ultima modificacion (UTC).
 */
public record EvaluacionDesempenoDto(
        UUID id,
        UUID empleadoId,
        String periodo,
        BigDecimal calificacion,
        String comentarios,
        Instant evaluadaEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link EvaluacionDesempeno} a su DTO de salida.
     *
     * @param evaluacion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static EvaluacionDesempenoDto de(EvaluacionDesempeno evaluacion) {
        return new EvaluacionDesempenoDto(
                evaluacion.getId(),
                evaluacion.getEmpleadoId(),
                evaluacion.getPeriodo(),
                evaluacion.getCalificacion(),
                evaluacion.getComentarios(),
                evaluacion.getEvaluadaEn(),
                evaluacion.getVersion(),
                evaluacion.getCreatedAt(),
                evaluacion.getUpdatedAt());
    }
}
