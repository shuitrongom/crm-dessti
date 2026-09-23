package com.dessti.crm.vertical.anuncios.levantamiento.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoFoto;

/**
 * DTO de salida de una {@link LevantamientoFoto} adjunta a un Levantamiento_Sitio
 * (Req 12.2, 16.3), distinto de la entidad de persistencia.
 *
 * @param id              identificador de la fotografia.
 * @param levantamientoId Levantamiento_Sitio propietario (Req 16.3).
 * @param referencia      URL o clave del objeto de la fotografia (Req 16.3).
 * @param createdAt       instante de alta (UTC).
 */
public record LevantamientoFotoDto(
        UUID id,
        UUID levantamientoId,
        String referencia,
        Instant createdAt) {

    /**
     * Proyecta una entidad {@link LevantamientoFoto} a su DTO de salida.
     *
     * @param foto entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static LevantamientoFotoDto de(LevantamientoFoto foto) {
        return new LevantamientoFotoDto(
                foto.getId(),
                foto.getLevantamientoId(),
                foto.getReferencia(),
                foto.getCreatedAt());
    }
}
