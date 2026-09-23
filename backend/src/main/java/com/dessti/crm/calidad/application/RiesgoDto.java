package com.dessti.crm.calidad.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.calidad.domain.Riesgo;

/**
 * DTO de salida de un {@link Riesgo} (Req 12.2, 70.3), distinto de la entidad de
 * persistencia. El {@code nivelDerivado} es de solo lectura (Req 70.3).
 *
 * @param id            identificador del Riesgo.
 * @param descripcion   descripcion del riesgo.
 * @param probabilidad  etiqueta de la probabilidad (baja/media/alta).
 * @param impacto       etiqueta del impacto (bajo/medio/alto).
 * @param nivelDerivado etiqueta del nivel derivado (bajo/medio/alto/critico), solo lectura.
 * @param acciones      acciones para abordarlo; {@code null} si no se registraron.
 * @param estado        etiqueta del estado.
 * @param version       version para concurrencia optimista (Req 49).
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record RiesgoDto(
        UUID id,
        String descripcion,
        String probabilidad,
        String impacto,
        String nivelDerivado,
        String acciones,
        String estado,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Riesgo} a su DTO de salida.
     *
     * @param riesgo entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static RiesgoDto de(Riesgo riesgo) {
        return new RiesgoDto(
                riesgo.getId(),
                riesgo.getDescripcion(),
                riesgo.getProbabilidad().valorBd(),
                riesgo.getImpacto().valorBd(),
                riesgo.getNivelDerivado().valorBd(),
                riesgo.getAcciones(),
                riesgo.getEstado().valorBd(),
                riesgo.getVersion(),
                riesgo.getCreatedAt(),
                riesgo.getUpdatedAt());
    }
}
