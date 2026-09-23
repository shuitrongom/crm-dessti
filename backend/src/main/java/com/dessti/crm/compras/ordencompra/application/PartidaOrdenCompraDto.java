package com.dessti.crm.compras.ordencompra.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.compras.ordencompra.domain.PartidaOrdenCompra;

/**
 * DTO de salida de una {@link PartidaOrdenCompra} (Req 12.2, 31.2), distinto de la
 * entidad de persistencia. El controlador REST lo serializa dentro del
 * {@link OrdenCompraDto}; nunca se expone la entidad JPA.
 *
 * @param id             identificador de la partida.
 * @param materialId     Material solicitado (Req 31.1).
 * @param cantidad       cantidad (entero en [1, 999,999]).
 * @param precioUnitario precio unitario (escala 2).
 * @param subtotal       subtotal = round(cantidad * precio_unitario, 2) (Req 31.3).
 */
public record PartidaOrdenCompraDto(
        UUID id,
        UUID materialId,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal) {

    /**
     * Proyecta una entidad {@link PartidaOrdenCompra} a su DTO de salida.
     *
     * @param partida entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PartidaOrdenCompraDto de(PartidaOrdenCompra partida) {
        return new PartidaOrdenCompraDto(
                partida.getId(),
                partida.getMaterialId(),
                partida.getCantidad(),
                partida.getPrecioUnitario(),
                partida.getSubtotal());
    }
}
