package com.dessti.crm.operacion.produccion.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;

/**
 * DTO de salida de una {@link OrdenFabricacion} (Req 12.2, 7), distinto de la
 * entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA. El estado se expone como su etiqueta de negocio ({@code pendiente},
 * {@code en_produccion}, {@code terminada}, {@code cancelada}) coherente con el
 * Req 7.5.
 *
 * @param id           identificador de la Orden_Fabricacion (Req 7.1).
 * @param cotizacionId Cotizacion aprobada de origen (Req 7.1, 7.3).
 * @param clienteId    Cliente de la Cotizacion; permite segmentar el listado (Req 7.9).
 * @param estado       etiqueta del estado (Req 7.4, 7.5).
 * @param version      version para concurrencia optimista (Req 49).
 * @param createdAt    instante de alta (UTC).
 * @param updatedAt    instante de la ultima modificacion (UTC).
 */
public record OrdenFabricacionDto(
        UUID id,
        UUID cotizacionId,
        UUID clienteId,
        String estado,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link OrdenFabricacion} a su DTO de salida.
     *
     * @param orden entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static OrdenFabricacionDto de(OrdenFabricacion orden) {
        return new OrdenFabricacionDto(
                orden.getId(),
                orden.getCotizacionId(),
                orden.getClienteId(),
                orden.getEstado().valorBd(),
                orden.getVersion(),
                orden.getCreatedAt(),
                orden.getUpdatedAt());
    }
}
