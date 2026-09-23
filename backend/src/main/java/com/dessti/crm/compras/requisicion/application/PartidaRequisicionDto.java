package com.dessti.crm.compras.requisicion.application;

import java.util.UUID;

import com.dessti.crm.compras.requisicion.domain.PartidaRequisicion;

/**
 * DTO de salida de una {@link PartidaRequisicion} (Req 12.2, 30.1), distinto de la
 * entidad de persistencia. El controlador REST lo serializa dentro del
 * {@link RequisicionCompraDto}; nunca se expone la entidad JPA.
 *
 * @param id         identificador de la partida.
 * @param materialId Material solicitado (Req 30.1).
 * @param cantidad   cantidad (entero en [1, 999,999]).
 */
public record PartidaRequisicionDto(
        UUID id,
        UUID materialId,
        int cantidad) {

    /**
     * Proyecta una entidad {@link PartidaRequisicion} a su DTO de salida.
     *
     * @param partida entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PartidaRequisicionDto de(PartidaRequisicion partida) {
        return new PartidaRequisicionDto(
                partida.getId(),
                partida.getMaterialId(),
                partida.getCantidad());
    }
}
