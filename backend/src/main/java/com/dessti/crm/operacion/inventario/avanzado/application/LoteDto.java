package com.dessti.crm.operacion.inventario.avanzado.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.operacion.inventario.avanzado.domain.Lote;

/**
 * DTO de salida de un {@link Lote} (Req 12.2, 60), distinto de la entidad de
 * persistencia.
 *
 * @param id             identificador del Lote.
 * @param materialId     Material al que pertenece el Lote.
 * @param codigo         codigo del Lote.
 * @param fechaCaducidad fecha de caducidad, o {@code null} si no aplica.
 * @param version        version para concurrencia optimista (Req 49).
 * @param createdAt      instante de alta (UTC).
 * @param updatedAt      instante de la ultima modificacion (UTC).
 */
public record LoteDto(
        UUID id,
        UUID materialId,
        String codigo,
        LocalDate fechaCaducidad,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Lote} a su DTO de salida.
     *
     * @param lote entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static LoteDto de(Lote lote) {
        return new LoteDto(
                lote.getId(),
                lote.getMaterialId(),
                lote.getCodigo(),
                lote.getFechaCaducidad(),
                lote.getVersion(),
                lote.getCreatedAt(),
                lote.getUpdatedAt());
    }
}
