package com.dessti.crm.compras.requisicion.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.compras.requisicion.domain.RequisicionCompra;

/**
 * DTO de salida de una {@link RequisicionCompra} (Req 12.2, 30), distinto de la
 * entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA. El estado se expone como su etiqueta de negocio ({@code borrador},
 * {@code enviada}, {@code aprobada}, {@code rechazada}, {@code cancelada}).
 *
 * @param id            identificador de la Requisicion_Compra.
 * @param estado        etiqueta del estado (Req 30.1, 30.2).
 * @param ordenCompraId Orden_Compra generada; {@code null} si aun no se genero
 *                      (Req 30.3).
 * @param partidas      partidas de la Requisicion_Compra (Req 30.1).
 * @param version       version para concurrencia optimista (Req 49).
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record RequisicionCompraDto(
        UUID id,
        String estado,
        UUID ordenCompraId,
        List<PartidaRequisicionDto> partidas,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link RequisicionCompra} a su DTO de salida, incluyendo
     * sus partidas.
     *
     * @param requisicion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static RequisicionCompraDto de(RequisicionCompra requisicion) {
        List<PartidaRequisicionDto> partidas = requisicion.getPartidas().stream()
                .map(PartidaRequisicionDto::de)
                .toList();
        return new RequisicionCompraDto(
                requisicion.getId(),
                requisicion.getEstado().valorBd(),
                requisicion.getOrdenCompraId(),
                partidas,
                requisicion.getVersion(),
                requisicion.getCreatedAt(),
                requisicion.getUpdatedAt());
    }
}
