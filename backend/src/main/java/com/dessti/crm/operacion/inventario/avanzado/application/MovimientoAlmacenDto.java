package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.inventario.avanzado.domain.MovimientoAlmacen;

/**
 * DTO de salida de una fila del Kardex por Almacen ({@link MovimientoAlmacen}) (Req
 * 12.2, 60), distinto de la entidad de persistencia. El tipo se expone como su etiqueta
 * de negocio ({@code entrada}, {@code salida}, {@code transferencia_salida},
 * {@code transferencia_entrada}, {@code ajuste}).
 *
 * @param id              identificador del movimiento.
 * @param almacenId       Almacen del movimiento.
 * @param materialId      Material afectado.
 * @param loteId          Lote afectado, o {@code null}.
 * @param tipo            etiqueta del tipo de movimiento (Req 60).
 * @param cantidad        cantidad del movimiento (positiva).
 * @param costoUnitario   costo unitario del movimiento.
 * @param costoTotal      costo total del movimiento.
 * @param saldoCantidad   snapshot de la cantidad en existencia tras el movimiento.
 * @param saldoCostoTotal snapshot del costo total en existencia tras el movimiento.
 * @param transferenciaId identificador de la transferencia asociada, o {@code null}.
 * @param motivo          nota opcional del movimiento.
 * @param version         version para concurrencia optimista (Req 49).
 * @param createdAt       instante del movimiento (UTC), orden del Kardex.
 */
public record MovimientoAlmacenDto(
        UUID id,
        UUID almacenId,
        UUID materialId,
        UUID loteId,
        String tipo,
        BigDecimal cantidad,
        BigDecimal costoUnitario,
        BigDecimal costoTotal,
        BigDecimal saldoCantidad,
        BigDecimal saldoCostoTotal,
        UUID transferenciaId,
        String motivo,
        long version,
        Instant createdAt) {

    /**
     * Proyecta una entidad {@link MovimientoAlmacen} a su DTO de salida (fila de Kardex).
     *
     * @param movimiento entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static MovimientoAlmacenDto de(MovimientoAlmacen movimiento) {
        return new MovimientoAlmacenDto(
                movimiento.getId(),
                movimiento.getAlmacenId(),
                movimiento.getMaterialId(),
                movimiento.getLoteId(),
                movimiento.getTipo().valorBd(),
                movimiento.getCantidad(),
                movimiento.getCostoUnitario(),
                movimiento.getCostoTotal(),
                movimiento.getSaldoCantidad(),
                movimiento.getSaldoCostoTotal(),
                movimiento.getTransferenciaId(),
                movimiento.getMotivo(),
                movimiento.getVersion(),
                movimiento.getCreatedAt());
    }
}
