package com.dessti.crm.operacion.inventario.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.inventario.domain.MovimientoInventario;

/**
 * DTO de salida de un {@link MovimientoInventario} (Req 12.2, 18.2), distinto de la
 * entidad de persistencia. El tipo se expone como su etiqueta de negocio
 * ({@code entrada}, {@code salida}, {@code ajuste}).
 *
 * @param id                     identificador del movimiento.
 * @param materialId             Material afectado.
 * @param tipo                   etiqueta del tipo de movimiento (Req 18.2).
 * @param cantidad               cantidad del movimiento (positiva).
 * @param existenciasResultantes existencias del Material tras aplicar el movimiento.
 * @param ordenFabricacionId     Orden_Fabricacion de origen si es un consumo (Req 18.4), o {@code null}.
 * @param motivo                 nota opcional del movimiento.
 * @param version                version para concurrencia optimista (Req 49).
 * @param createdAt              instante del movimiento (UTC).
 */
public record MovimientoInventarioDto(
        UUID id,
        UUID materialId,
        String tipo,
        BigDecimal cantidad,
        BigDecimal existenciasResultantes,
        UUID ordenFabricacionId,
        String motivo,
        long version,
        Instant createdAt) {

    /**
     * Proyecta una entidad {@link MovimientoInventario} a su DTO de salida.
     *
     * @param movimiento entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static MovimientoInventarioDto de(MovimientoInventario movimiento) {
        return new MovimientoInventarioDto(
                movimiento.getId(),
                movimiento.getMaterialId(),
                movimiento.getTipo().valorBd(),
                movimiento.getCantidad(),
                movimiento.getExistenciasResultantes(),
                movimiento.getOrdenFabricacionId(),
                movimiento.getMotivo(),
                movimiento.getVersion(),
                movimiento.getCreatedAt());
    }
}
