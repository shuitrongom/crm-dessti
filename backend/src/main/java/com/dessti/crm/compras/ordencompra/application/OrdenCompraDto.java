package com.dessti.crm.compras.ordencompra.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.compras.ordencompra.domain.OrdenCompra;

/**
 * DTO de salida de una {@link OrdenCompra} (Req 12.2, 31), distinto de la entidad
 * de persistencia. El controlador REST lo serializa; nunca se expone la entidad
 * JPA. El estado se expone como su etiqueta de negocio ({@code abierta},
 * {@code recibida_parcial}, {@code recibida_total}, {@code cerrada},
 * {@code cancelada}) coherente con el Req 31.5.
 *
 * @param id                  identificador de la Orden_Compra (Req 31.4).
 * @param proveedorId         Proveedor al que pertenece (Req 31.1).
 * @param requisicionCompraId Requisicion_Compra de origen; {@code null} en alta
 *                            directa (Req 30.3).
 * @param estado              etiqueta del estado (Req 31.5).
 * @param total               total = round(Σ subtotales, 2) (Req 31.3).
 * @param partidas            partidas de la Orden_Compra (Req 31.2).
 * @param version             version para concurrencia optimista (Req 49).
 * @param createdAt           instante de alta (UTC).
 * @param updatedAt           instante de la ultima modificacion (UTC).
 */
public record OrdenCompraDto(
        UUID id,
        UUID proveedorId,
        UUID requisicionCompraId,
        String estado,
        BigDecimal total,
        List<PartidaOrdenCompraDto> partidas,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link OrdenCompra} a su DTO de salida, incluyendo sus
     * partidas.
     *
     * @param orden entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static OrdenCompraDto de(OrdenCompra orden) {
        List<PartidaOrdenCompraDto> partidas = orden.getPartidas().stream()
                .map(PartidaOrdenCompraDto::de)
                .toList();
        return new OrdenCompraDto(
                orden.getId(),
                orden.getProveedorId(),
                orden.getRequisicionCompraId(),
                orden.getEstado().valorBd(),
                orden.getTotal(),
                partidas,
                orden.getVersion(),
                orden.getCreatedAt(),
                orden.getUpdatedAt());
    }
}
