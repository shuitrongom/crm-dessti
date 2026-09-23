package com.dessti.crm.operacion.produccion.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de la peticion para reemplazar las partidas de una Orden_Fabricacion (§B1):
 * {@code POST /ordenes-fabricacion/{id}/partidas} con
 * {@code { partidas: [{ materialId, cantidad }] }}. DTO de entrada del contrato REST,
 * distinto del comando de aplicacion y de la entidad JPA.
 *
 * <p>Validacion declarativa (mapeada a 422 por {@code ManejadorGlobalErrores}): cada
 * partida exige {@code materialId} y una {@code cantidad} positiva (cantidad &le; 0
 * &rarr; 422, Req 5.2). La lista puede omitirse/estar vacia (deja la OF sin
 * partidas); se normaliza a lista vacia. El servicio verifica ademas que la OF este
 * en {@code pendiente} (422) y que cada Material sea accesible (404, Req 5.3).</p>
 *
 * @param partidas partidas de consumo que reemplazan a las existentes; opcional.
 */
public record ReemplazarPartidasRequest(@Valid List<PartidaRequest> partidas) {

    public ReemplazarPartidasRequest {
        partidas = (partidas == null) ? List.of() : List.copyOf(partidas);
    }

    /**
     * Partida de consumo del cuerpo de la peticion: Material y cantidad positiva.
     *
     * @param materialId identificador del Material a consumir; obligatorio.
     * @param cantidad   cantidad a consumir; obligatoria y positiva (Req 5.1, 5.2).
     */
    public record PartidaRequest(
            @NotNull UUID materialId,
            @NotNull @Positive BigDecimal cantidad) {
    }
}
