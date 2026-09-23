package com.dessti.crm.comercial.producto.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para asignar el precio de un Producto en una
 * Lista_Precios (Req 59.3, 59.10). DTO de entrada del contrato REST, distinto de
 * la entidad y del comando
 * {@link com.dessti.crm.comercial.producto.application.AsignarPrecioCommand}
 * (Req 12.2). El {@code tenant_id} se deriva del contexto (Req 23.4).
 *
 * <p>El rango del precio ({@code [0.01, 999,999,999.99]}) lo valida el dominio
 * (422, Req 59.10; Property 30); aqui solo se exige su presencia.</p>
 *
 * @param productoId identificador del Producto; obligatorio.
 * @param precio     precio unitario; obligatorio.
 */
public record AsignarPrecioRequest(
        @NotNull UUID productoId,
        @NotNull BigDecimal precio) {
}
