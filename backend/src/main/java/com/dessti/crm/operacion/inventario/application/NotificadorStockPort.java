package com.dessti.crm.operacion.inventario.application;

/**
 * Puerto de dominio para emitir la Notificacion de una condicion de stock bajo de
 * un Material (Req 18.5).
 *
 * <p>Desacopla la deteccion de stock bajo (en {@code ServicioInventario}, tras
 * aplicar un Movimiento_Inventario) de la entrega efectiva de la Notificacion,
 * preservando la portabilidad del nucleo (arquitectura hexagonal). Sigue el mismo
 * patron que {@code NotificadorAlertasPort} del modulo de auditoria.</p>
 *
 * <p><strong>TODO (Tarea 43 - notificaciones):</strong> integrar este puerto con el
 * {@code NotificacionPort} del modulo de notificaciones (correo/WhatsApp con politica
 * de reintentos, Req 46) para entregar la alerta al Usuario responsable del inventario
 * por el canal correspondiente. Mientras tanto se provee
 * {@link NotificadorStockRegistroLog} como implementacion minima (placeholder) que
 * unicamente registra la condicion en el log; se registra como bean por defecto solo
 * si no existe otra implementacion ({@code @ConditionalOnMissingBean}), de modo que el
 * adaptador real de la Tarea 43 la reemplace automaticamente.</p>
 */
public interface NotificadorStockPort {

    /**
     * Emite la Notificacion de una condicion de stock bajo detectada (Req 18.5).
     *
     * @param notificacion metadatos del Material en stock bajo (sin secretos).
     */
    void notificarStockBajo(NotificacionStockBajo notificacion);
}
