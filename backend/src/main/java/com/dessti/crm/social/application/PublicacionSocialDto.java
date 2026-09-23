package com.dessti.crm.social.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.social.domain.PublicacionSocial;

/**
 * DTO de salida de una {@link PublicacionSocial} (Req 12.2, 65.1), distinto de la
 * entidad de persistencia.
 *
 * @param id                  identificador de la Publicacion_Social.
 * @param cuentaCanalSocialId Cuenta_Canal_Social por la que se publica.
 * @param canal               etiqueta del Canal_Social.
 * @param contenido           contenido de la publicacion.
 * @param fechaProgramada     instante programado de publicacion (UTC).
 * @param estado              etiqueta del estado (borrador/programada/publicada/fallida).
 * @param externoId           id externo del proveedor; {@code null} si no publicada.
 * @param publicadaEn         instante de publicacion confirmada (UTC); {@code null} si no aplica.
 * @param motivoFallo         motivo del fallo; {@code null} salvo estado {@code fallida}.
 * @param version             version para concurrencia optimista (Req 49).
 * @param createdAt           instante de alta (UTC).
 * @param updatedAt           instante de la ultima modificacion (UTC).
 */
public record PublicacionSocialDto(
        UUID id,
        UUID cuentaCanalSocialId,
        String canal,
        String contenido,
        Instant fechaProgramada,
        String estado,
        String externoId,
        Instant publicadaEn,
        String motivoFallo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link PublicacionSocial} a su DTO de salida.
     *
     * @param publicacion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PublicacionSocialDto de(PublicacionSocial publicacion) {
        return new PublicacionSocialDto(
                publicacion.getId(),
                publicacion.getCuentaCanalSocialId(),
                publicacion.getCanal().valorBd(),
                publicacion.getContenido(),
                publicacion.getFechaProgramada(),
                publicacion.getEstado().valorBd(),
                publicacion.getExternoId(),
                publicacion.getPublicadaEn(),
                publicacion.getMotivoFallo(),
                publicacion.getVersion(),
                publicacion.getCreatedAt(),
                publicacion.getUpdatedAt());
    }
}
