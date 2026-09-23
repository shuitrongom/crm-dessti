package com.dessti.crm.compras.recepcion.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.compras.recepcion.domain.PartidaRecepcion;

/**
 * DTO de salida de un {@link PartidaRecepcion} (Req 12.2, 32.1), distinto de la
 * entidad de persistencia. El controlador REST lo serializa dentro del
 * {@link RecepcionMercanciaDto}; nunca se expone la entidad JPA.
 *
 * @param id                   identificador del renglon.
 * @param partidaOrdenCompraId Partida_Orden_Compra contra la que se recibio.
 * @param materialId           Material recibido (Req 32.4).
 * @param cantidadRecibida     cantidad recibida (estrictamente positiva).
 */
public record PartidaRecepcionDto(
        UUID id,
        UUID partidaOrdenCompraId,
        UUID materialId,
        BigDecimal cantidadRecibida) {

    /**
     * Proyecta una entidad {@link PartidaRecepcion} a su DTO de salida.
     *
     * @param partida entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PartidaRecepcionDto de(PartidaRecepcion partida) {
        return new PartidaRecepcionDto(
                partida.getId(),
                partida.getPartidaOrdenCompraId(),
                partida.getMaterialId(),
                partida.getCantidadRecibida());
    }
}
