package com.dessti.crm.operacion.proyecto.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.proyecto.domain.Sitio;

/**
 * DTO de salida de un {@link Sitio} (Req 12.2, 21.2), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 *
 * @param id         identificador del Sitio (Req 21.2).
 * @param proyectoId Proyecto al que pertenece (Req 21.2).
 * @param nombre     nombre del Sitio (Req 21.2).
 * @param direccion  direccion fisica; {@code null} si no se proporciono.
 * @param version    version para concurrencia optimista (Req 49).
 * @param createdAt  instante de alta (UTC).
 * @param updatedAt  instante de la ultima modificacion (UTC).
 */
public record SitioDto(
        UUID id,
        UUID proyectoId,
        String nombre,
        String direccion,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Sitio} a su DTO de salida.
     *
     * @param sitio entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static SitioDto de(Sitio sitio) {
        return new SitioDto(
                sitio.getId(),
                sitio.getProyectoId(),
                sitio.getNombre(),
                sitio.getDireccion(),
                sitio.getVersion(),
                sitio.getCreatedAt(),
                sitio.getUpdatedAt());
    }
}
