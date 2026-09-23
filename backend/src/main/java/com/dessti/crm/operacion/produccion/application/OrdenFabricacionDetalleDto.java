package com.dessti.crm.operacion.produccion.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.PartidaOrdenFabricacion;

/**
 * DTO de salida enriquecido de una {@link OrdenFabricacion} para la consulta puntual
 * (Req 5.5, §B1): los mismos campos que {@link OrdenFabricacionDto} mas la lista de
 * sus partidas (Material + cantidad). Lo devuelve {@code GET /ordenes-fabricacion/{id}};
 * el listado ({@code GET /ordenes-fabricacion}) conserva el DTO de resumen
 * {@link OrdenFabricacionDto} para no cambiar su forma.
 *
 * @param id           identificador de la Orden_Fabricacion (Req 7.1).
 * @param cotizacionId Cotizacion de origen; {@code null} en el origen generico (Req 1.1).
 * @param clienteId    Cliente asociado (Req 7.9).
 * @param estado       etiqueta del estado (Req 7.4, 7.5).
 * @param version      version para concurrencia optimista (Req 49).
 * @param createdAt    instante de alta (UTC).
 * @param updatedAt    instante de la ultima modificacion (UTC).
 * @param partidas     partidas de consumo de la OF (Material + cantidad); lista vacia
 *                     si no hay (Req 5.5).
 */
public record OrdenFabricacionDetalleDto(
        UUID id,
        UUID cotizacionId,
        UUID clienteId,
        String estado,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<PartidaOrdenFabricacionDto> partidas) {

    /**
     * Compone el DTO de detalle a partir de la Orden_Fabricacion y sus partidas.
     *
     * @param orden    entidad de la Orden_Fabricacion.
     * @param partidas partidas de consumo de la OF; se proyectan a DTO. Nunca
     *                 {@code null} (se normaliza a lista vacia inmutable).
     * @return el DTO de detalle correspondiente.
     */
    public static OrdenFabricacionDetalleDto de(OrdenFabricacion orden,
                                                List<PartidaOrdenFabricacion> partidas) {
        List<PartidaOrdenFabricacionDto> partidasDto = (partidas == null)
                ? List.of()
                : partidas.stream().map(PartidaOrdenFabricacionDto::de).toList();
        return new OrdenFabricacionDetalleDto(
                orden.getId(),
                orden.getCotizacionId(),
                orden.getClienteId(),
                orden.getEstado().valorBd(),
                orden.getVersion(),
                orden.getCreatedAt(),
                orden.getUpdatedAt(),
                partidasDto);
    }
}
