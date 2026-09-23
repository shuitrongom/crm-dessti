package com.dessti.crm.compras.factura.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.compras.factura.domain.FacturaProveedor;

/**
 * DTO de salida de una {@link FacturaProveedor} (Req 12.2, 33), distinto de la
 * entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA. El estado se expone como su etiqueta de negocio ({@code registrada},
 * {@code conciliada}, {@code discrepancia}, {@code pagada}) coherente con el
 * Req 33.6.
 *
 * @param id             identificador de la factura (Req 33.1).
 * @param ordenCompraId  Orden_Compra asociada (Req 33.1).
 * @param proveedorId    Proveedor de la Orden_Compra (denormalizado, Req 33.8).
 * @param folioProveedor folio de factura del Proveedor (Req 33.2).
 * @param monto          monto de la factura (escala 2, Req 33.2).
 * @param estado         etiqueta del estado (Req 33.6).
 * @param version        version para concurrencia optimista (Req 49).
 * @param createdAt      instante de alta (UTC).
 * @param updatedAt      instante de la ultima modificacion (UTC).
 */
public record FacturaProveedorDto(
        UUID id,
        UUID ordenCompraId,
        UUID proveedorId,
        String folioProveedor,
        BigDecimal monto,
        String estado,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link FacturaProveedor} a su DTO de salida.
     *
     * @param factura entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static FacturaProveedorDto de(FacturaProveedor factura) {
        return new FacturaProveedorDto(
                factura.getId(),
                factura.getOrdenCompraId(),
                factura.getProveedorId(),
                factura.getFolioProveedor(),
                factura.getMonto(),
                factura.getEstado().valorBd(),
                factura.getVersion(),
                factura.getCreatedAt(),
                factura.getUpdatedAt());
    }
}
