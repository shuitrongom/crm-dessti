package com.dessti.crm.social.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.social.domain.PlantillaMensaje;

/**
 * DTO de salida de una {@link PlantillaMensaje} (Req 12.2, 64.7), distinto de la
 * entidad de persistencia.
 *
 * @param id        identificador de la Plantilla_Mensaje.
 * @param canal     etiqueta del Canal_Social.
 * @param nombre    nombre de la plantilla.
 * @param contenido contenido de la plantilla.
 * @param aprobada  {@code true} si esta aprobada por el proveedor (Req 64.7).
 * @param version   version para concurrencia optimista (Req 49).
 * @param createdAt instante de alta (UTC).
 * @param updatedAt instante de la ultima modificacion (UTC).
 */
public record PlantillaMensajeDto(
        UUID id,
        String canal,
        String nombre,
        String contenido,
        boolean aprobada,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link PlantillaMensaje} a su DTO de salida.
     *
     * @param plantilla entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PlantillaMensajeDto de(PlantillaMensaje plantilla) {
        return new PlantillaMensajeDto(
                plantilla.getId(),
                plantilla.getCanal().valorBd(),
                plantilla.getNombre(),
                plantilla.getContenido(),
                plantilla.isAprobada(),
                plantilla.getVersion(),
                plantilla.getCreatedAt(),
                plantilla.getUpdatedAt());
    }
}
