package com.dessti.crm.vertical.anuncios.permiso.application;

/**
 * Puerto de dominio para emitir la Notificacion de vencimiento proximo de un
 * Permiso_Instalacion aprobado (Req 17.5).
 *
 * <p>Desacopla la deteccion del vencimiento proximo (en {@code ServicioPermisos},
 * al invocar {@code notificarVencimientosProximos()}) de la entrega efectiva de la
 * Notificacion, preservando la portabilidad del nucleo (arquitectura hexagonal).
 * Sigue el mismo patron que {@code NotificadorStockPort} del submodulo de
 * inventario.</p>
 *
 * <p><strong>TODO (Tarea 43 / bloque 49 - notificaciones):</strong> integrar este
 * puerto con el {@code NotificacionPort} del modulo de notificaciones (correo/WhatsApp
 * con politica de reintentos, Req 46) para entregar la alerta al Usuario responsable
 * del Sitio por el canal correspondiente, y programar la invocacion periodica de
 * {@code notificarVencimientosProximos()} (por ejemplo, con un planificador diario).
 * Mientras tanto se provee {@link NotificadorPermisoRegistroLog} como implementacion
 * minima (placeholder) que unicamente registra la condicion en el log; se registra
 * como bean por defecto solo si no existe otra implementacion
 * ({@code @ConditionalOnMissingBean}), de modo que el adaptador real la reemplace
 * automaticamente.</p>
 */
public interface NotificadorPermisoPort {

    /**
     * Emite la Notificacion de un vencimiento proximo de un Permiso_Instalacion
     * aprobado (Req 17.5).
     *
     * @param notificacion metadatos del permiso proximo a vencer (sin secretos).
     */
    void notificarVencimientoProximo(NotificacionVencimientoPermiso notificacion);
}
