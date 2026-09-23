package com.dessti.crm.vertical.anuncios.permiso.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Descripcion inmutable del vencimiento proximo de un {@link
 * com.dessti.crm.vertical.anuncios.permiso.domain.PermisoInstalacion} aprobado (Req 17.5),
 * lista para notificarse al Usuario responsable del Sitio a traves del
 * {@link NotificadorPermisoPort}.
 *
 * <p>No contiene secretos: solo metadatos del permiso y su fecha de vencimiento. El
 * contenido y el canal del mensaje final (correo/WhatsApp) los compondra el
 * adaptador real de notificaciones (Tarea 43/bloque 49), que sustituira la
 * implementacion por defecto. Sigue el patron de {@code NotificacionStockBajo} del
 * submodulo de inventario.</p>
 *
 * @param tenantId         empresa a la que pertenece el permiso (Req 23).
 * @param permisoId        identificador del Permiso_Instalacion afectado.
 * @param sitioId          Sitio vinculado (para localizar al responsable); puede ser {@code null}.
 * @param tipo             etiqueta del tipo del permiso ({@code municipal}/{@code arrendador}).
 * @param fechaVencimiento fecha de vencimiento del permiso.
 * @param diasParaVencer   dias restantes hasta el vencimiento (>= 0), relativos a hoy.
 */
public record NotificacionVencimientoPermiso(
        UUID tenantId,
        UUID permisoId,
        UUID sitioId,
        String tipo,
        LocalDate fechaVencimiento,
        long diasParaVencer) {
}
