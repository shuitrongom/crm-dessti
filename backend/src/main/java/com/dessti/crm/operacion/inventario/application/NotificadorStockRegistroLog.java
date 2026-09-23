package com.dessti.crm.operacion.inventario.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementacion minima (placeholder) del {@link NotificadorStockPort} que se limita
 * a registrar la condicion de stock bajo en el log de la aplicacion (Req 18.5).
 *
 * <p>Existe para que la deteccion de stock bajo funcione de extremo a extremo sin
 * acoplar el modulo de inventario a un proveedor de notificaciones concreto. Se
 * registra como bean por defecto en {@link InventarioConfig} solo si <strong>no hay
 * otra implementacion</strong> del puerto ({@code @ConditionalOnMissingBean}), de modo
 * que cuando la Tarea 43 aporte el adaptador real de notificaciones (correo/WhatsApp),
 * este placeholder se desactive automaticamente.</p>
 *
 * <p>No registra secretos: {@link NotificacionStockBajo} solo transporta metadatos del
 * Material y su saldo.</p>
 *
 * <p><strong>TODO (Tarea 43):</strong> sustituir por la integracion real con el
 * {@code NotificacionPort} del modulo de notificaciones.</p>
 */
public class NotificadorStockRegistroLog implements NotificadorStockPort {

    private static final Logger log = LoggerFactory.getLogger(NotificadorStockRegistroLog.class);

    @Override
    public void notificarStockBajo(NotificacionStockBajo notificacion) {
        // Placeholder: la entrega real la implementa la Tarea 43 (notificaciones).
        log.warn("[STOCK_BAJO] tenant={} material={} nombre='{}' existencias={} stockMinimo={}"
                        + " (notificacion placeholder; integrar con Tarea 43)",
                notificacion.tenantId(),
                notificacion.materialId(),
                notificacion.nombre(),
                notificacion.existencias(),
                notificacion.stockMinimo());
    }
}
