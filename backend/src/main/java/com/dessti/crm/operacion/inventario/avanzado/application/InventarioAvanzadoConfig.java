package com.dessti.crm.operacion.inventario.avanzado.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del submodulo de inventario avanzado por Almacen (Req 60).
 *
 * <p>Registra el {@link NotificadorInventarioAvanzadoRegistroLog} como implementacion
 * por defecto del {@link NotificadorInventarioAvanzadoPort} <strong>solo si no existe
 * otra implementacion</strong> en el contexto ({@code @ConditionalOnMissingBean}). Asi,
 * las notificaciones de stock minimo/maximo y reabastecimiento del Req 60 funcionan de
 * extremo a extremo desde ya (registrando en el log), y cuando la Tarea 43 aporte el
 * adaptador real de notificaciones (correo/WhatsApp), este placeholder se desactiva
 * automaticamente. Sigue el mismo patron que {@code InventarioConfig} del inventario
 * base (Req 18).</p>
 */
@Configuration
public class InventarioAvanzadoConfig {

    /**
     * Registra el notificador de inventario avanzado placeholder si no hay otra
     * implementacion.
     *
     * @return el notificador que registra las condiciones en el log.
     */
    @Bean
    @ConditionalOnMissingBean(NotificadorInventarioAvanzadoPort.class)
    public NotificadorInventarioAvanzadoPort notificadorInventarioAvanzadoPorDefecto() {
        return new NotificadorInventarioAvanzadoRegistroLog();
    }
}
