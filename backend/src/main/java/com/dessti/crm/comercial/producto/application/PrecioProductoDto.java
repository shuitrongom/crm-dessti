package com.dessti.crm.comercial.producto.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.comercial.producto.domain.PrecioProducto;

/**
 * DTO de salida de un {@link PrecioProducto} (Req 12.2, 59.3), distinto de la
 * entidad de persistencia.
 *
 * @param id              identificador del precio.
 * @param listaPreciosId  Lista_Precios a la que pertenece.
 * @param productoId      Producto al que aplica.
 * @param precio          precio unitario (escala 2, Req 59.3).
 * @param version         version para concurrencia optimista (Req 49).
 */
public record PrecioProductoDto(
        UUID id,
        UUID listaPreciosId,
        UUID productoId,
        BigDecimal precio,
        long version) {

    /**
     * Proyecta una entidad {@link PrecioProducto} a su DTO de salida.
     *
     * @param precio entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PrecioProductoDto de(PrecioProducto precio) {
        return new PrecioProductoDto(
                precio.getId(),
                precio.getListaPreciosId(),
                precio.getProductoId(),
                precio.getPrecio(),
                precio.getVersion());
    }
}
