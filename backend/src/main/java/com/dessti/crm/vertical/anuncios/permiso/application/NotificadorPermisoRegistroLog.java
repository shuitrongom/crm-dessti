package com.dessti.crm.vertical.anuncios.permiso.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementacion minima (placeholder) del {@link NotificadorPermisoPort} que se
 * limita a registrar el vencimiento proximo de un Permiso_Instalacion en el log de
 * la aplicacion (Req 17.5).
 *
 * <p>Existe para que la deteccion de vencimientos proximos funcione de extremo a
 * extremo sin acoplar el submodulo de permisos a un proveedor de notificaciones
 * concreto. Se registra como bean por defecto en {@link PermisoConfig} solo si
 * <strong>no hay otra implementacion</strong> del puerto
 * ({@code @ConditionalOnMissingBean}), de modo que cuando la Tarea 43/bloque 49
 * aporte el adaptador real de notificaciones (correo/WhatsApp), este placeholder se
 * desactive automaticamente. Sigue el mismo patron que
 * {@code NotificadorStockRegistroLog}.</p>
 *
 * <p>No registra secretos: {@link NotificacionVencimientoPermiso} solo transporta
 * metadatos del permiso y su fecha de vencimiento.</p>
 */
public class NotificadorPermisoRegistroLog implements NotificadorPermisoPort {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPermisoRegistroLog.class);

    @Override
    public void notificarVencimientoProximo(NotificacionVencimientoPermiso notificacion) {
        // Placeholder: la entrega real la implementa la Tarea 43/bloque 49 (notificaciones).
        log.warn("[PERMISO_POR_VENCER] tenant={} permiso={} sitio={} tipo='{}' vence={} enDias={}"
                        + " (notificacion placeholder; integrar con Tarea 43/bloque 49)",
                notificacion.tenantId(),
                notificacion.permisoId(),
                notificacion.sitioId(),
                notificacion.tipo(),
                notificacion.fechaVencimiento(),
                notificacion.diasParaVencer());
    }
}
