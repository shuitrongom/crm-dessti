package com.dessti.crm.compras.requisicion.adapter.in.rest;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Cuerpo de la peticion para dar de alta una Requisicion_Compra (Req 30.1). DTO de
 * entrada del contrato REST, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.compras.requisicion.application.CrearRequisicionCompraCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * <p>La lista de partidas debe tener al menos 1 elemento (Req 30.1); la cota se
 * valida por Bean Validation ({@code @NotEmpty}) y tambien la refuerza el dominio
 * (422).</p>
 *
 * @param partidas partidas de la Requisicion_Compra; al menos 1 (Req 30.1).
 */
public record CrearRequisicionCompraRequest(
        @NotEmpty @Valid List<PartidaRequisicionRequest> partidas) {
}
