package com.dessti.crm.compras.recepcion.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.compras.recepcion.domain.RecepcionMercancia;

/**
 * DTO de salida de una {@link RecepcionMercancia} (Req 12.2, 32), distinto de la
 * entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA.
 *
 * @param id            identificador de la recepcion (Req 32.1).
 * @param ordenCompraId Orden_Compra contra la que se recibio (Req 32.1).
 * @param recibidaEn    instante UTC de la recepcion.
 * @param partidas      renglones recibidos (Req 32.1).
 * @param version       version para concurrencia optimista (Req 49).
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record RecepcionMercanciaDto(
        UUID id,
        UUID ordenCompraId,
        Instant recibidaEn,
        List<PartidaRecepcionDto> partidas,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link RecepcionMercancia} a su DTO de salida, incluyendo
     * sus renglones.
     *
     * @param recepcion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static RecepcionMercanciaDto de(RecepcionMercancia recepcion) {
        List<PartidaRecepcionDto> partidas = recepcion.getPartidas().stream()
                .map(PartidaRecepcionDto::de)
                .toList();
        return new RecepcionMercanciaDto(
                recepcion.getId(),
                recepcion.getOrdenCompraId(),
                recepcion.getRecibidaEn(),
                partidas,
                recepcion.getVersion(),
                recepcion.getCreatedAt(),
                recepcion.getUpdatedAt());
    }
}
