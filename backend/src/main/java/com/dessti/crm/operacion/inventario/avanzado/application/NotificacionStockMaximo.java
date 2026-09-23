package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Descripcion inmutable de una condicion de stock MAXIMO (por encima del stock maximo
 * configurado) detectada para un Material en un Almacen (Req 60), lista para notificarse
 * a traves del {@link NotificadorInventarioAvanzadoPort}.
 *
 * <p>No contiene secretos: solo metadatos del Almacen/Material y las cantidades.</p>
 *
 * @param tenantId   empresa a la que pertenece el Material (Req 23).
 * @param almacenId  Almacen afectado.
 * @param materialId Material afectado.
 * @param nombre     nombre del Material (para el mensaje); puede ser {@code null}.
 * @param cantidad   cantidad actual en el Almacen.
 * @param umbral     stock maximo que se cruzo al alza.
 */
public record NotificacionStockMaximo(
        UUID tenantId,
        UUID almacenId,
        UUID materialId,
        String nombre,
        BigDecimal cantidad,
        BigDecimal umbral) {
}
