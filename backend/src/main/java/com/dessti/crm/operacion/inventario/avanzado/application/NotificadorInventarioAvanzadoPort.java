package com.dessti.crm.operacion.inventario.avanzado.application;

/**
 * Puerto de dominio para emitir las Notificaciones del inventario avanzado por Almacen
 * (Req 60): stock minimo (punto de reorden), stock maximo y sugerencia de
 * reabastecimiento.
 *
 * <p>Desacopla la deteccion de las condiciones (en {@code ServicioInventarioAvanzado},
 * al evaluar un saldo frente a la configuracion del Material) de la entrega efectiva de
 * la Notificacion, preservando la portabilidad del nucleo (arquitectura hexagonal).
 * Sigue el mismo patron que {@code NotificadorStockPort} del inventario base (Req 18).</p>
 *
 * <p><strong>TODO (Tarea 43 - notificaciones):</strong> integrar este puerto con el
 * {@code NotificacionPort} del modulo de notificaciones (correo/WhatsApp con politica de
 * reintentos, Req 46). Mientras tanto se provee {@link NotificadorInventarioAvanzadoRegistroLog}
 * como implementacion minima (placeholder) que solo registra la condicion en el log; se
 * registra como bean por defecto solo si no existe otra implementacion
 * ({@code @ConditionalOnMissingBean}), de modo que el adaptador real de la Tarea 43 la
 * reemplace automaticamente.</p>
 */
public interface NotificadorInventarioAvanzadoPort {

    /**
     * Emite la Notificacion de una condicion de stock minimo (por debajo del punto de
     * reorden) detectada (Req 60).
     *
     * @param notificacion metadatos del Material/Almacen afectado (sin secretos).
     */
    void notificarStockMinimo(NotificacionStockMinimo notificacion);

    /**
     * Emite la Notificacion de una condicion de stock maximo (por encima del stock
     * maximo configurado) detectada (Req 60).
     *
     * @param notificacion metadatos del Material/Almacen afectado (sin secretos).
     */
    void notificarStockMaximo(NotificacionStockMaximo notificacion);

    /**
     * Emite la sugerencia de reabastecimiento de un Material en un Almacen (Req 60).
     *
     * @param notificacion metadatos del Material/Almacen afectado (sin secretos).
     */
    void notificarReabastecimiento(NotificacionReabastecimiento notificacion);
}
