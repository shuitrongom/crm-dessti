package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Descripcion inmutable de una condicion de stock MINIMO (por debajo del punto de
 * reorden) detectada para un Material en un Almacen (Req 60), lista para notificarse a
 * traves del {@link NotificadorInventarioAvanzadoPort}.
 *
 * <p>No contiene secretos: solo metadatos del Almacen/Material y las cantidades. El
 * contenido y el canal del mensaje final (correo/WhatsApp) los compondra el adaptador
 * real de notificaciones (Tarea 43), que sustituira la implementacion por defecto.</p>
 *
 * @param tenantId   empresa a la que pertenece el Material (Req 23).
 * @param almacenId  Almacen afectado.
 * @param materialId Material afectado.
 * @param nombre     nombre del Material (para el mensaje); puede ser {@code null}.
 * @param cantidad   cantidad actual en el Almacen.
 * @param umbral     punto de reorden que se cruzo a la baja.
 */
public record NotificacionStockMinimo(
        UUID tenantId,
        UUID almacenId,
        UUID materialId,
        String nombre,
        BigDecimal cantidad,
        BigDecimal umbral) {
}
