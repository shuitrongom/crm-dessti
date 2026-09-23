package com.dessti.crm.comercial.cotizacion.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion;

/**
 * DTO de salida de una {@link PartidaCotizacion} (Req 12.2, 6.3), distinto de la
 * entidad de persistencia. El controlador REST lo serializa dentro del
 * {@link CotizacionDto}; nunca se expone la entidad JPA.
 *
 * @param id             identificador de la partida.
 * @param productoId     Producto referido; {@code null} si es texto libre (Req 59.4).
 * @param descripcion    descripcion de la partida.
 * @param cantidad       cantidad (entero en [1, 999,999]).
 * @param precioUnitario precio unitario (escala 2).
 * @param subtotal       subtotal = round(cantidad * precio_unitario, 2) (Req 6.3).
 */
public record PartidaCotizacionDto(
        UUID id,
        UUID productoId,
        String descripcion,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal) {

    /**
     * Proyecta una entidad {@link PartidaCotizacion} a su DTO de salida.
     *
     * @param partida entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PartidaCotizacionDto de(PartidaCotizacion partida) {
        return new PartidaCotizacionDto(
                partida.getId(),
                partida.getProductoId(),
                partida.getDescripcion(),
                partida.getCantidad(),
                partida.getPrecioUnitario(),
                partida.getSubtotal());
    }
}
