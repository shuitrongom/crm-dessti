package com.dessti.crm.comercial.cotizacion.adapter.in.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta una Cotizacion (Req 6.1, 6.2). DTO de
 * entrada del contrato REST, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.comercial.cotizacion.application.CrearCotizacionCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * <p>La lista de partidas debe tener entre 1 y 500 elementos (Req 6.1, 6.2); la
 * cota se valida por Bean Validation ({@code @NotEmpty} + {@code @Size}) y tambien
 * la refuerza el dominio (422).</p>
 *
 * <p>Los campos descriptivos son OPCIONALES (V60): fecha de vigencia, condiciones,
 * notas y moneda. El folio y la fecha de emision NO se reciben: los asigna el
 * servidor (folio consecutivo por tenant/anio; fecha de emision = hoy).</p>
 *
 * @param clienteId   Cliente existente al que se asocia; obligatorio (Req 6.1).
 * @param partidas    partidas de la Cotizacion; entre 1 y 500 (Req 6.1, 6.2).
 * @param validoHasta fecha de vigencia; opcional, &ge; fecha de emision (V60).
 * @param condiciones terminos y condiciones; opcional, max. 2000 (V60).
 * @param notas       notas libres; opcional, max. 2000 (V60).
 * @param moneda      moneda ISO 4217; {@code null}/blanco usa {@code MXN} (V60).
 */
public record CrearCotizacionRequest(
        @NotNull UUID clienteId,
        @NotEmpty @Size(max = 500) @Valid List<PartidaRequest> partidas,
        LocalDate validoHasta,
        @Size(max = 2000) String condiciones,
        @Size(max = 2000) String notas,
        @Size(max = 3) String moneda) {
}
