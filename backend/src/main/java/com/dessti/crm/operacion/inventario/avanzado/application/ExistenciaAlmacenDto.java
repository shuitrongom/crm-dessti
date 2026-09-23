package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.inventario.avanzado.domain.ExistenciaAlmacen;

/**
 * DTO de salida del saldo de existencias de un Material en un Almacen (Req 12.2, 60),
 * distinto de la entidad de persistencia.
 *
 * @param id            identificador del saldo.
 * @param almacenId     Almacen al que pertenece el saldo.
 * @param materialId    Material al que pertenece el saldo.
 * @param cantidad      cantidad en existencia en el Almacen.
 * @param costoPromedio costo promedio ponderado por unidad en el Almacen.
 * @param version       version para concurrencia optimista (Req 49).
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record ExistenciaAlmacenDto(
        UUID id,
        UUID almacenId,
        UUID materialId,
        BigDecimal cantidad,
        BigDecimal costoPromedio,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ExistenciaAlmacen} a su DTO de salida.
     *
     * @param existencia entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ExistenciaAlmacenDto de(ExistenciaAlmacen existencia) {
        return new ExistenciaAlmacenDto(
                existencia.getId(),
                existencia.getAlmacenId(),
                existencia.getMaterialId(),
                existencia.getCantidad(),
                existencia.getCostoPromedio(),
                existencia.getVersion(),
                existencia.getCreatedAt(),
                existencia.getUpdatedAt());
    }
}
