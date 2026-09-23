package com.dessti.crm.compras.ordencompra.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta una Orden_Compra (Req 31.1). DTO de
 * entrada del contrato REST, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.compras.ordencompra.application.CrearOrdenCompraCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * <p>La lista de partidas debe tener entre 1 y 500 elementos (Req 31.1); la cota
 * se valida por Bean Validation ({@code @NotEmpty} + {@code @Size}) y tambien la
 * refuerza el dominio (422).</p>
 *
 * @param proveedorId Proveedor existente al que se asocia; obligatorio (Req 31.1).
 * @param partidas    partidas de la Orden_Compra; entre 1 y 500 (Req 31.1).
 */
public record CrearOrdenCompraRequest(
        @NotNull UUID proveedorId,
        @NotEmpty @Size(max = 500) @Valid List<PartidaOrdenCompraRequest> partidas) {
}
