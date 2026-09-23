package com.dessti.crm.calidad.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.calidad.domain.ContextoOrganizacion;

/**
 * DTO de salida de un {@link ContextoOrganizacion} (Req 12.2, 70.5), distinto de la
 * entidad de persistencia.
 *
 * @param id              identificador del Contexto_Organizacion.
 * @param cuestion        cuestion determinada.
 * @param tipo            etiqueta del tipo (interna/externa).
 * @param climaPertinente indicador de pertinencia del cambio climatico.
 * @param justificacion   justificacion de la determinacion (conservada siempre).
 * @param parteInteresada parte interesada; {@code null} si no aplica.
 * @param expectativa     expectativa de la parte interesada; {@code null} si no aplica.
 * @param version         version para concurrencia optimista (Req 49).
 * @param createdAt       instante de alta (UTC).
 * @param updatedAt       instante de la ultima modificacion (UTC).
 */
public record ContextoOrganizacionDto(
        UUID id,
        String cuestion,
        String tipo,
        boolean climaPertinente,
        String justificacion,
        String parteInteresada,
        String expectativa,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ContextoOrganizacion} a su DTO de salida.
     *
     * @param contexto entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ContextoOrganizacionDto de(ContextoOrganizacion contexto) {
        return new ContextoOrganizacionDto(
                contexto.getId(),
                contexto.getCuestion(),
                contexto.getTipo().valorBd(),
                contexto.isClimaPertinente(),
                contexto.getJustificacion(),
                contexto.getParteInteresada(),
                contexto.getExpectativa(),
                contexto.getVersion(),
                contexto.getCreatedAt(),
                contexto.getUpdatedAt());
    }
}
