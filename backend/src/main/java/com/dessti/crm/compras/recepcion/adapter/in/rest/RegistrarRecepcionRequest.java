package com.dessti.crm.compras.recepcion.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar una Recepcion_Mercancia (Req 32.1). DTO de
 * entrada del contrato REST, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.compras.recepcion.application.RegistrarRecepcionCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * <p>La lista de renglones debe tener entre 1 y 500 elementos (coherente con el
 * maximo de partidas de la Orden_Compra); la cota se valida por Bean Validation
 * ({@code @NotEmpty} + {@code @Size}) y tambien la refuerza el dominio.</p>
 *
 * @param ordenCompraId Orden_Compra contra la que se recibe; obligatorio (Req 32.1).
 * @param partidas      renglones recibidos; entre 1 y 500.
 */
public record RegistrarRecepcionRequest(
        @NotNull UUID ordenCompraId,
        @NotEmpty @Size(max = 500) @Valid List<PartidaRecepcionRequest> partidas) {
}
