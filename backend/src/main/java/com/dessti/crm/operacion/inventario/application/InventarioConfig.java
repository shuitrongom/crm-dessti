package com.dessti.crm.operacion.inventario.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del submodulo de inventario de Materiales (Req 18).
 *
 * <p>Registra el {@link NotificadorStockRegistroLog} como implementacion por defecto
 * del {@link NotificadorStockPort} <strong>solo si no existe otra implementacion</strong>
 * en el contexto ({@code @ConditionalOnMissingBean}). Asi, la notificacion de stock
 * bajo del Req 18.5 funciona de extremo a extremo desde ya (registrando en el log), y
 * cuando la Tarea 43 aporte el adaptador real de notificaciones (correo/WhatsApp),
 * este placeholder se desactiva automaticamente. Sigue el mismo patron que
 * {@code AuditoriaConfig} para {@code NotificadorAlertasPort}.</p>
 */
@Configuration
public class InventarioConfig {

    /**
     * Registra el notificador de stock bajo placeholder si no hay otra implementacion.
     *
     * @return el notificador que registra la condicion de stock bajo en el log.
     */
    @Bean
    @ConditionalOnMissingBean(NotificadorStockPort.class)
    public NotificadorStockPort notificadorStockPorDefecto() {
        return new NotificadorStockRegistroLog();
    }
}
