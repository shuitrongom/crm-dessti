package com.dessti.crm.operacion.produccion.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de la peticion para crear una Orden_Fabricacion por el <strong>origen
 * generico</strong> (Req 1.1): {@code POST /ordenes-fabricacion/directa} con
 * {@code { clienteId, partidas: [{ materialId, cantidad }] }}. DTO de entrada del
 * contrato REST, distinto del comando de aplicacion y de la entidad JPA.
 *
 * <p>Validacion declarativa (mapeada a 422 por {@code ManejadorGlobalErrores}):</p>
 * <ul>
 *   <li>{@code clienteId} obligatorio (Req 1.4); su existencia real (404) la
 *       verifica el servicio via {@code ClienteExistentePort} (Req 1.5).</li>
 *   <li>Cada partida exige {@code materialId} y una {@code cantidad} positiva
 *       (cantidad &le; 0 &rarr; 422, Req 5.2). La lista puede omitirse/estar vacia;
 *       se normaliza a lista vacia.</li>
 * </ul>
 *
 * <p><strong>Nota (tarea 3.1, §B1):</strong> la accesibilidad de cada Material (404)
 * y la persistencia de las partidas pertenecen a la tarea 3.1; en la tarea 1.8 las
 * partidas se aceptan y validan estructuralmente, pero solo se persiste la OF.</p>
 *
 * @param clienteId Cliente al que se asocia la OF; obligatorio (Req 1.4).
 * @param partidas  partidas iniciales de consumo; opcional (por defecto vacia).
 */
public record CrearOrdenDirectaRequest(
        @NotNull UUID clienteId,
        @Valid List<PartidaRequest> partidas) {

    public CrearOrdenDirectaRequest {
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
