package com.dessti.crm.calidad.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.calidad.domain.NoConformidad;

/**
 * DTO de salida de una {@link NoConformidad} (Req 12.2, 70.2), distinto de la entidad
 * de persistencia.
 *
 * @param id              identificador de la No_Conformidad.
 * @param origen          etiqueta del origen.
 * @param descripcion     descripcion.
 * @param procesoAfectado proceso afectado.
 * @param detectadaEn     instante de deteccion (UTC).
 * @param estado          etiqueta del estado (abierta/en_tratamiento/cerrada).
 * @param version         version para concurrencia optimista (Req 49).
 * @param createdAt       instante de alta (UTC).
 * @param updatedAt       instante de la ultima modificacion (UTC).
 */
public record NoConformidadDto(
        UUID id,
        String origen,
        String descripcion,
        String procesoAfectado,
        Instant detectadaEn,
        String estado,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link NoConformidad} a su DTO de salida.
     *
     * @param noConformidad entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static NoConformidadDto de(NoConformidad noConformidad) {
        return new NoConformidadDto(
                noConformidad.getId(),
                noConformidad.getOrigen().valorBd(),
                noConformidad.getDescripcion(),
                noConformidad.getProcesoAfectado(),
                noConformidad.getDetectadaEn(),
                noConformidad.getEstado().valorBd(),
                noConformidad.getVersion(),
                noConformidad.getCreatedAt(),
                noConformidad.getUpdatedAt());
    }
}
