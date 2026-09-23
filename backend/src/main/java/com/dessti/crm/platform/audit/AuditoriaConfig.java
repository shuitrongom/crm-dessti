package com.dessti.crm.platform.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del modulo de auditoria (Req 10).
 *
 * <p>Habilita el enlace tipado de {@link AuditoriaProperties} (retencion
 * configurable, Req 10.8), siguiendo el mismo patron que el resto de la
 * plataforma. El {@link ServicioAuditoria}, el
 * {@link VerificadorCadenaAuditoria} y el {@link ServicioAlertasAuditoria} se
 * registran por component scanning.</p>
 */
@Configuration
@EnableConfigurationProperties(AuditoriaProperties.class)
public class AuditoriaConfig {

    /**
     * Registra el {@link NotificadorAlertasRegistroLog} como implementacion por
     * defecto del {@link NotificadorAlertasPort} <strong>solo si no existe otra
     * implementacion</strong> en el contexto. Cuando la Tarea 43 aporte el
     * adaptador real de notificaciones, este placeholder se desactiva.
     *
     * @return el notificador placeholder que registra las alertas en el log.
     */
    @Bean
    @ConditionalOnMissingBean(NotificadorAlertasPort.class)
    public NotificadorAlertasPort notificadorAlertasPorDefecto() {
        return new NotificadorAlertasRegistroLog();
    }
}
