package com.dessti.crm.facturacion.adapter.out.pac;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dessti.crm.facturacion.application.PacPort;

/**
 * Configuracion del adaptador del PAC (Req 35.8, 11). Registra
 * {@link PacProperties} como bean de propiedades, resuelto desde el entorno
 * (variables {@code PAC_USER}/{@code PAC_PASSWORD}/{@code PAC_URL}), siguiendo el
 * mismo patron que {@code SecretosConfig}.
 *
 * <p>El bean {@link PacPort} lo aporta el {@link PacStubAdapter}, registrado aqui
 * mediante un metodo {@code @Bean} {@code @ConditionalOnMissingBean} (patron fiable
 * de {@code ReportesBiConfig}/{@code InventarioConfig}), mientras no exista el
 * adaptador HTTP real, que en el futuro consumira estas propiedades.</p>
 */
@Configuration
@EnableConfigurationProperties(PacProperties.class)
public class PacConfig {

    /**
     * Registra el PAC stub como implementacion por defecto de {@link PacPort} si no
     * hay otra implementacion. Recibe el {@link Clock} del contexto (UTC) para fijar
     * la fecha de Timbrado de forma sustituible en pruebas.
     *
     * @param clock reloj del sistema aportado como bean.
     * @return el adaptador stub del PAC.
     */
    @Bean
    @ConditionalOnMissingBean(PacPort.class)
    public PacPort pacPorDefecto(Clock clock) {
        return new PacStubAdapter(clock);
    }
}
