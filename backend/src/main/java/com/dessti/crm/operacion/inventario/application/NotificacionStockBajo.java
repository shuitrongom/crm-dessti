package com.dessti.crm.operacion.inventario.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Descripcion inmutable de una condicion de stock bajo detectada tras un
 * Movimiento_Inventario (Req 18.5), lista para notificarse al Usuario responsable
 * del inventario a traves del {@link NotificadorStockPort}.
 *
 * <p>No contiene secretos: solo metadatos del Material y su saldo. El contenido y
 * el canal del mensaje final (correo/WhatsApp) los compondra el adaptador real de
 * notificaciones (Tarea 43), que sustituira la implementacion por defecto.</p>
 *
 * @param tenantId     empresa a la que pertenece el Material (Req 23).
 * @param materialId   identificador del Material afectado.
 * @param nombre       nombre del Material (para el mensaje).
 * @param existencias  existencias actuales tras el movimiento.
 * @param stockMinimo  stock minimo configurado del Material.
 */
public record NotificacionStockBajo(
        UUID tenantId,
        UUID materialId,
        String nombre,
        BigDecimal existencias,
        BigDecimal stockMinimo) {
}
