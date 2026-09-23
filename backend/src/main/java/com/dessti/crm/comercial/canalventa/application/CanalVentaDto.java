package com.dessti.crm.comercial.canalventa.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.comercial.canalventa.domain.CanalVenta;

/**
 * DTO de salida de un {@link CanalVenta} (Req 12.2, 63), distinto de la entidad
 * de persistencia. El controlador REST lo serializa; nunca se expone la entidad
 * JPA.
 *
 * @param id          identificador del Canal_Venta.
 * @param nombre      nombre del canal.
 * @param descripcion descripcion; puede ser {@code null} (Req 63.1).
 * @param activo      {@code true} si el canal esta vigente (no dado de baja).
 * @param version     version para concurrencia optimista (Req 49).
 * @param createdAt   instante de alta (UTC).
 * @param updatedAt   instante de la ultima modificacion (UTC).
 */
public record CanalVentaDto(
        UUID id,
        String nombre,
        String descripcion,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link CanalVenta} a su DTO de salida.
     *
     * @param canal entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static CanalVentaDto de(CanalVenta canal) {
        return new CanalVentaDto(
                canal.getId(),
                canal.getNombre(),
                canal.getDescripcion(),
                canal.isActivo(),
                canal.getVersion(),
                canal.getCreatedAt(),
                canal.getUpdatedAt());
    }
}
