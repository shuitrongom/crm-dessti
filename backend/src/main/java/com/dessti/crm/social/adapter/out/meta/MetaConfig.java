package com.dessti.crm.social.adapter.out.meta;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dessti.crm.social.application.MensajeriaSocialPort;
import com.dessti.crm.social.application.PublicacionSocialPort;

/**
 * Configuracion del adaptador de mensajeria de Meta (Req 64, 65, 11). Registra
 * {@link MetaProperties} como bean de propiedades, resuelto desde el entorno
 * (variables {@code META_APP_SECRET}/{@code META_VERIFY_TOKEN}/
 * {@code META_ACCESS_TOKEN} y la politica de reintentos), siguiendo el mismo patron
 * que {@code PacConfig}.
 *
 * <p>El bean {@link MensajeriaSocialPort} lo aporta el {@link MetaStubAdapter} y el
 * bean {@link PublicacionSocialPort} el {@link PublicacionSocialStubAdapter}, ambos
 * registrados aqui mediante metodos {@code @Bean} {@code @ConditionalOnMissingBean}
 * (patron fiable de {@code ReportesBiConfig}/{@code InventarioConfig}), mientras no
 * existan los adaptadores HTTP reales por canal (WhatsApp Business Cloud API, Graph
 * API), que en el futuro consumiran estas propiedades.</p>
 */
@Configuration
@EnableConfigurationProperties(MetaProperties.class)
public class MetaConfig {

    /**
     * Registra el stub de mensajeria como implementacion por defecto de
     * {@link MensajeriaSocialPort} si no hay otra implementacion.
     *
     * @return el adaptador stub de mensajeria de Meta.
     */
    @Bean
    @ConditionalOnMissingBean(MensajeriaSocialPort.class)
    public MensajeriaSocialPort mensajeriaSocialPorDefecto() {
        return new MetaStubAdapter();
    }

    /**
     * Registra el stub de publicacion como implementacion por defecto de
     * {@link PublicacionSocialPort} si no hay otra implementacion.
     *
     * @return el adaptador stub de publicacion social de Meta.
     */
    @Bean
    @ConditionalOnMissingBean(PublicacionSocialPort.class)
    public PublicacionSocialPort publicacionSocialPorDefecto() {
        return new PublicacionSocialStubAdapter();
    }
}
