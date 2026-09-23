package com.dessti.crm.portalcliente.adapter.out.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dessti.crm.portalcliente.application.ClientePortalActualPort;

/**
 * Configuracion de seguridad del Portal del Cliente (Req 45.1, 45.3).
 *
 * <p>Registra el {@link ClientePortalActualDesdeAuthenticationAdapter} como
 * implementacion por defecto del {@link ClientePortalActualPort} <strong>solo si no
 * existe otra implementacion</strong> en el contexto ({@code @ConditionalOnMissingBean}),
 * mediante el patron fiable de metodo {@code @Bean} en una clase
 * {@code @Configuration} (como {@code PacConfig}/{@code MetaConfig}/{@code CaptacionLeadConfig}).
 * Asi el Portal resuelve el Cliente del usuario a partir de las authorities del
 * token desde ya, y cuando se introduzca un mecanismo definitivo de vinculo
 * Usuario -&gt; Cliente (claim dedicado en el JWT o tabla de vinculo) bastara aportar
 * otro bean {@link ClientePortalActualPort} y este adaptador por defecto se desactiva
 * automaticamente, sin tocar el resto del Portal.</p>
 */
@Configuration
public class PortalClienteSeguridadConfig {

    /**
     * Registra el adaptador por defecto que resuelve el Cliente del Portal desde el
     * {@code Authentication} vigente si no hay otra implementacion en el contexto.
     *
     * @return el puerto de resolucion del Cliente actual del Portal por defecto.
     */
    @Bean
    @ConditionalOnMissingBean(ClientePortalActualPort.class)
    public ClientePortalActualPort clientePortalActualPorDefecto() {
        return new ClientePortalActualDesdeAuthenticationAdapter();
    }
}
