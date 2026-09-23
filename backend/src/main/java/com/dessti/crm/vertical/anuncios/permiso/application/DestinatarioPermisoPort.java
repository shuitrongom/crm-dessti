package com.dessti.crm.vertical.anuncios.permiso.application;

import java.util.Optional;

import com.dessti.crm.vertical.anuncios.permiso.domain.PermisoInstalacion;

/**
 * Puerto de lectura mínimo que resuelve el correo destinatario al que dirigir la
 * Notificación de vencimiento próximo de un {@link PermisoInstalacion} (Req 13.2,
 * §E1, Decisión D7).
 *
 * <p>La resolución es explícita y minimalista: se intenta el correo del responsable
 * del Sitio si estuviera disponible y, en su defecto, el correo de contacto de la
 * Empresa del tenant en contexto. El {@link PermisoInstalacion} del giro anuncios
 * (entidad {@code Sitio}) <strong>no</strong> almacena hoy un responsable con correo,
 * por lo que el adaptador de referencia resuelve el contacto de la Empresa del
 * tenant. Este puerto aísla esa política del adaptador de notificación, preservando
 * la arquitectura hexagonal (mismo patrón de puertos de lectura del resto del núcleo).</p>
 *
 * <p>Si no hay destinatario resoluble se devuelve {@link Optional#empty()}, y el
 * notificador registra la omisión sin romper el barrido (Req 13.2, §E1).</p>
 */
public interface DestinatarioPermisoPort {

    /**
     * Resuelve el correo destinatario de la notificación de vencimiento de un
     * permiso próximo a vencer.
     *
     * @param notificacion metadatos del permiso por vencer (incluye tenant y Sitio).
     * @return el correo destinatario, o {@link Optional#empty()} si no hay ninguno
     *         resoluble en el tenant.
     */
    Optional<String> resolverCorreo(NotificacionVencimientoPermiso notificacion);
}
