package com.dessti.crm.calidad.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.calidad.domain.CambioSgc;

/**
 * DTO de salida de un {@link CambioSgc} (Req 12.2, 70.4), distinto de la entidad de
 * persistencia.
 *
 * @param id                       identificador del Cambio_SGC.
 * @param titulo                   titulo.
 * @param proposito                proposito.
 * @param consecuenciasPotenciales consecuencias potenciales.
 * @param recursosNecesarios       recursos necesarios.
 * @param responsableId            Usuario responsable.
 * @param estado                   etiqueta del estado.
 * @param aprobadoPor              actor que aprobo; {@code null} hasta la aprobacion.
 * @param aprobadoEn               instante de aprobacion (UTC); {@code null} hasta aprobar.
 * @param version                  version para concurrencia optimista (Req 49).
 * @param createdAt                instante de alta (UTC).
 * @param updatedAt                instante de la ultima modificacion (UTC).
 */
public record CambioSgcDto(
        UUID id,
        String titulo,
        String proposito,
        String consecuenciasPotenciales,
        String recursosNecesarios,
        UUID responsableId,
        String estado,
        String aprobadoPor,
        Instant aprobadoEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link CambioSgc} a su DTO de salida.
     *
     * @param cambio entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static CambioSgcDto de(CambioSgc cambio) {
        return new CambioSgcDto(
                cambio.getId(),
                cambio.getTitulo(),
                cambio.getProposito(),
                cambio.getConsecuenciasPotenciales(),
                cambio.getRecursosNecesarios(),
                cambio.getResponsableId(),
                cambio.getEstado().valorBd(),
                cambio.getAprobadoPor(),
                cambio.getAprobadoEn(),
                cambio.getVersion(),
                cambio.getCreatedAt(),
                cambio.getUpdatedAt());
    }
}
