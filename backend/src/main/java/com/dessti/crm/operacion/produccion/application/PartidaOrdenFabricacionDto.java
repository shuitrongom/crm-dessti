package com.dessti.crm.operacion.produccion.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.operacion.produccion.domain.PartidaOrdenFabricacion;

/**
 * DTO de salida de una {@link PartidaOrdenFabricacion} (Req 5.5, §B1), distinto de la
 * entidad de persistencia. Expone el Material y la cantidad de la partida; el
 * frontend resuelve el nombre del Material y nunca muestra el UUID crudo (§D2,
 * Req 5.5).
 *
 * @param materialId identificador del Material consumido (Req 5.1).
 * @param cantidad   cantidad a consumir; positiva (Req 5.1, 5.2).
 */
public record PartidaOrdenFabricacionDto(UUID materialId, BigDecimal cantidad) {

    /**
     * Proyecta una entidad {@link PartidaOrdenFabricacion} a su DTO de salida.
     *
     * @param partida entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PartidaOrdenFabricacionDto de(PartidaOrdenFabricacion partida) {
        return new PartidaOrdenFabricacionDto(partida.getMaterialId(), partida.getCantidad());
    }
}
