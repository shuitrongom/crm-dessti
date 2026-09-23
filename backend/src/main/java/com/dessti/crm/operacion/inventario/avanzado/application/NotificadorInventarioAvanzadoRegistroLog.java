package com.dessti.crm.operacion.inventario.avanzado.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementacion minima (placeholder) del {@link NotificadorInventarioAvanzadoPort} que
 * se limita a registrar las condiciones de stock minimo/maximo y reabastecimiento en el
 * log de la aplicacion (Req 60).
 *
 * <p>Existe para que la deteccion funcione de extremo a extremo sin acoplar el submodulo
 * de inventario avanzado a un proveedor de notificaciones concreto. Se registra como bean
 * por defecto en {@link InventarioAvanzadoConfig} solo si <strong>no hay otra
 * implementacion</strong> del puerto ({@code @ConditionalOnMissingBean}), de modo que
 * cuando la Tarea 43 aporte el adaptador real de notificaciones (correo/WhatsApp), este
 * placeholder se desactive automaticamente.</p>
 *
 * <p>No registra secretos: las notificaciones solo transportan metadatos del Almacen/
 * Material y las cantidades. Sigue el mismo patron que {@code NotificadorStockRegistroLog}.</p>
 *
 * <p><strong>TODO (Tarea 43):</strong> sustituir por la integracion real con el
 * {@code NotificacionPort} del modulo de notificaciones.</p>
 */
public class NotificadorInventarioAvanzadoRegistroLog implements NotificadorInventarioAvanzadoPort {

    private static final Logger log =
            LoggerFactory.getLogger(NotificadorInventarioAvanzadoRegistroLog.class);

    @Override
    public void notificarStockMinimo(NotificacionStockMinimo notificacion) {
        // Placeholder: la entrega real la implementa la Tarea 43 (notificaciones).
        log.warn("[STOCK_MINIMO] tenant={} almacen={} material={} nombre='{}' cantidad={} umbral={}"
                        + " (notificacion placeholder; integrar con Tarea 43)",
                notificacion.tenantId(), notificacion.almacenId(), notificacion.materialId(),
                notificacion.nombre(), notificacion.cantidad(), notificacion.umbral());
    }

    @Override
    public void notificarStockMaximo(NotificacionStockMaximo notificacion) {
        // Placeholder: la entrega real la implementa la Tarea 43 (notificaciones).
        log.warn("[STOCK_MAXIMO] tenant={} almacen={} material={} nombre='{}' cantidad={} umbral={}"
                        + " (notificacion placeholder; integrar con Tarea 43)",
                notificacion.tenantId(), notificacion.almacenId(), notificacion.materialId(),
                notificacion.nombre(), notificacion.cantidad(), notificacion.umbral());
    }

    @Override
    public void notificarReabastecimiento(NotificacionReabastecimiento notificacion) {
        // Placeholder: la entrega real la implementa la Tarea 43 (notificaciones).
        log.warn("[REABASTECIMIENTO] tenant={} almacen={} material={} nombre='{}' cantidad={} umbral={}"
                        + " (notificacion placeholder; integrar con Tarea 43)",
                notificacion.tenantId(), notificacion.almacenId(), notificacion.materialId(),
                notificacion.nombre(), notificacion.cantidad(), notificacion.umbral());
    }
}
