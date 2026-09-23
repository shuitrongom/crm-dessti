package com.dessti.crm.vertical.anuncios.instalacion.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.instalacion.domain.PendienteInstalacion;

/**
 * DTO de salida de una entrada de la Lista_Pendientes de una
 * Orden_Trabajo_Instalacion (Req 12.2, 19.4), distinto de la entidad de
 * persistencia.
 *
 * @param id                        identificador del pendiente.
 * @param ordenTrabajoInstalacionId Orden_Trabajo_Instalacion propietaria.
 * @param descripcion               descripcion de la tarea pendiente (Req 19.4).
 * @param resuelto                  indica si el pendiente esta resuelto (Req 19.6).
 * @param version                   version para concurrencia optimista (Req 49).
 * @param createdAt                 instante de alta (UTC).
 * @param updatedAt                 instante de la ultima modificacion (UTC).
 */
public record PendienteInstalacionDto(
        UUID id,
        UUID ordenTrabajoInstalacionId,
        String descripcion,
        boolean resuelto,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link PendienteInstalacion} a su DTO de salida.
     *
     * @param pendiente entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PendienteInstalacionDto de(PendienteInstalacion pendiente) {
        return new PendienteInstalacionDto(
                pendiente.getId(),
                pendiente.getOrdenTrabajoInstalacionId(),
                pendiente.getDescripcion(),
                pendiente.isResuelto(),
                pendiente.getVersion(),
                pendiente.getCreatedAt(),
                pendiente.getUpdatedAt());
    }
}
