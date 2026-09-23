package com.dessti.crm.estrategia.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.estrategia.domain.EsenciaEmpresa;

/**
 * DTO de salida de la {@link EsenciaEmpresa} (mision, vision, valores; Req 58.1),
 * distinto de la entidad de persistencia. El controlador REST lo serializa; nunca
 * se expone la entidad JPA.
 *
 * @param id        identificador de la esencia.
 * @param mision    mision de la Empresa (Req 58.1); puede ser {@code null}.
 * @param vision    vision de la Empresa (Req 58.1); puede ser {@code null}.
 * @param valores   valores de la Empresa (Req 58.1); puede ser {@code null}.
 * @param version   version para concurrencia optimista (Req 49).
 * @param createdAt instante de alta (UTC).
 * @param updatedAt instante de la ultima modificacion (UTC).
 */
public record EsenciaEmpresaDto(
        UUID id,
        String mision,
        String vision,
        String valores,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link EsenciaEmpresa} a su DTO de salida.
     *
     * @param esencia entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static EsenciaEmpresaDto de(EsenciaEmpresa esencia) {
        return new EsenciaEmpresaDto(
                esencia.getId(),
                esencia.getMision(),
                esencia.getVision(),
                esencia.getValores(),
                esencia.getVersion(),
                esencia.getCreatedAt(),
                esencia.getUpdatedAt());
    }
}
