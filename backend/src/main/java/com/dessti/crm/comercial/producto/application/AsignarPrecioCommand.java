package com.dessti.crm.comercial.producto.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando para asignar (o actualizar) el precio de un Producto dentro de una
 * Lista_Precios (Req 59.3, 59.10). Distinto de la entidad; no incluye el
 * {@code tenant_id} (Req 23.4).
 *
 * @param productoId identificador del Producto; obligatorio.
 * @param precio     precio unitario; obligatorio y dentro de
 *                   {@code [0.01, 999,999,999.99]}.
 */
public record AsignarPrecioCommand(
        UUID productoId,
        BigDecimal precio) {
}
