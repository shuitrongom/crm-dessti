package com.dessti.crm.social.adapter.out.lead;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dessti.crm.social.application.CaptacionLeadPort;

/**
 * Configuracion de la captacion de leads del modulo social (Req 64.4).
 *
 * <p>Registra el {@link CaptacionLeadStubAdapter} como implementacion por defecto
 * del {@link CaptacionLeadPort} <strong>solo si no existe otra implementacion</strong>
 * en el contexto ({@code @ConditionalOnMissingBean}), mediante el patron fiable de
 * metodo {@code @Bean} en una clase {@code @Configuration} (como
 * {@code ReportesBiConfig}/{@code InventarioConfig}). Asi el modulo social opera de
 * forma independiente del modulo {@code comercial.cliente} desde ya, y cuando el
 * modulo comercial aporte un adaptador real que cree el Contacto/Oportunidad
 * efectivos, este placeholder se desactiva automaticamente.</p>
 */
@Configuration
public class CaptacionLeadConfig {

    /**
     * Registra el stub de captacion de leads si no hay otra implementacion.
     *
     * @return el adaptador por defecto de captacion de leads.
     */
    @Bean
    @ConditionalOnMissingBean(CaptacionLeadPort.class)
    public CaptacionLeadPort captacionLeadPorDefecto() {
        return new CaptacionLeadStubAdapter();
    }
}
