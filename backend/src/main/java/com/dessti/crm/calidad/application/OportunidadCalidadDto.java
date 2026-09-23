package com.dessti.crm.calidad.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.calidad.domain.OportunidadCalidad;

/**
 * DTO de salida de una {@link OportunidadCalidad} (Req 12.2, 70.3), distinto de la
 * entidad de persistencia.
 *
 * @param id                identificador de la Oportunidad_Calidad.
 * @param descripcion       descripcion.
 * @param beneficioEsperado beneficio esperado.
 * @param acciones          acciones para aprovecharla; {@code null} si no se registraron.
 * @param estado            etiqueta del estado.
 * @param version           version para concurrencia optimista (Req 49).
 * @param createdAt         instante de alta (UTC).
 * @param updatedAt         instante de la ultima modificacion (UTC).
 */
public record OportunidadCalidadDto(
        UUID id,
        String descripcion,
        String beneficioEsperado,
        String acciones,
        String estado,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link OportunidadCalidad} a su DTO de salida.
     *
     * @param oportunidad entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static OportunidadCalidadDto de(OportunidadCalidad oportunidad) {
        return new OportunidadCalidadDto(
                oportunidad.getId(),
                oportunidad.getDescripcion(),
                oportunidad.getBeneficioEsperado(),
                oportunidad.getAcciones(),
                oportunidad.getEstado().valorBd(),
                oportunidad.getVersion(),
                oportunidad.getCreatedAt(),
                oportunidad.getUpdatedAt());
    }
}
