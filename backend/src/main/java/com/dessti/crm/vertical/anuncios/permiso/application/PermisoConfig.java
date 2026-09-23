package com.dessti.crm.vertical.anuncios.permiso.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del submodulo de permisos de instalacion (Req 17).
 *
 * <p>Registra el {@link NotificadorPermisoRegistroLog} como implementacion por
 * defecto del {@link NotificadorPermisoPort} <strong>solo si no existe otra
 * implementacion</strong> en el contexto ({@code @ConditionalOnMissingBean}). Asi, la
 * notificacion de vencimiento proximo del Req 17.5 funciona de extremo a extremo
 * desde ya (registrando en el log), y cuando la Tarea 43/bloque 49 aporte el
 * adaptador real de notificaciones (correo/WhatsApp), este placeholder se desactiva
 * automaticamente. Sigue el mismo patron que {@code InventarioConfig} para
 * {@code NotificadorStockPort}.</p>
 */
@Configuration
public class PermisoConfig {

    /**
     * Registra el notificador de vencimiento de permisos placeholder si no hay otra
     * implementacion.
     *
     * @return el notificador que registra el vencimiento proximo en el log.
     */
    @Bean
    @ConditionalOnMissingBean(NotificadorPermisoPort.class)
    public NotificadorPermisoPort notificadorPermisoPorDefecto() {
        return new NotificadorPermisoRegistroLog();
    }
}
