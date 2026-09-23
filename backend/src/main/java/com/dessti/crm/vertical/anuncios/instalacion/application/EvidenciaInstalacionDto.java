package com.dessti.crm.vertical.anuncios.instalacion.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.instalacion.domain.EvidenciaInstalacion;

/**
 * DTO de salida de una evidencia fotografica de una Orden_Trabajo_Instalacion
 * (Req 12.2, 19.4), distinto de la entidad de persistencia.
 *
 * @param id                        identificador de la evidencia.
 * @param ordenTrabajoInstalacionId Orden_Trabajo_Instalacion propietaria.
 * @param url                       referencia a la fotografia (URL o clave de objeto).
 * @param version                   version para concurrencia optimista (Req 49).
 * @param createdAt                 instante de alta (UTC).
 * @param updatedAt                 instante de la ultima modificacion (UTC).
 */
public record EvidenciaInstalacionDto(
        UUID id,
        UUID ordenTrabajoInstalacionId,
        String url,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link EvidenciaInstalacion} a su DTO de salida.
     *
     * @param evidencia entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static EvidenciaInstalacionDto de(EvidenciaInstalacion evidencia) {
        return new EvidenciaInstalacionDto(
                evidencia.getId(),
                evidencia.getOrdenTrabajoInstalacionId(),
                evidencia.getUrl(),
                evidencia.getVersion(),
                evidencia.getCreatedAt(),
                evidencia.getUpdatedAt());
    }
}
