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
 * @param descuento      descuento (monto) de la partida (escala 2, V80).
 * @param importeBase    importe bruto = cantidad * precio_unitario (escala 2, V80).
 * @param tasaIva        etiqueta de la tasa de IVA ({@code 16}/{@code 8}/{@code 0}/
 *                       {@code exento}) (V80).
 * @param iva            IVA de la partida sobre su base neta (escala 2, V80).
 * @param subtotal       base neta = importe bruto - descuento (escala 2, V80).
 */
public record PartidaCotizacionDto(
        UUID id,
        UUID productoId,
        String descripcion,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuento,
        BigDecimal importeBase,
        String tasaIva,
        BigDecimal iva,
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
                partida.getDescuento(),
                partida.getImporteBase(),
                partida.getTasaIva().valorBd(),
                partida.getIva(),
                partida.getSubtotal());
    }
}
