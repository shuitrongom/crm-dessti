package com.dessti.crm.platform.tenant;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de infraestructura multi-tenant de la capa web (Req 23).
 *
 * <p>Registra el {@link TenantResolutionFilter} con un orden que lo situa
 * <strong>despues</strong> de la cadena de filtros de Spring Security, de modo
 * que el contexto de autenticacion ya este disponible cuando se resuelve el
 * {@code tenant_id}.</p>
 *
 * <p>La cadena de {@code springSecurityFilterChain} se registra por defecto en
 * {@code SecurityProperties.DEFAULT_FILTER_ORDER} (-100). Al registrar este
 * filtro con un orden mayor (por ejemplo, 0), se garantiza que se ejecute a
 * continuacion de la seguridad.</p>
 */
@Configuration
public class TenantWebConfig {

    /**
     * Orden del filtro de resolucion de tenant. Debe ser mayor que el de la
     * cadena de Spring Security ({@code -100}) para ejecutarse despues de ella.
     */
    public static final int TENANT_FILTER_ORDER = 0;

    @Bean
    public FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilterRegistration(
            TenantFilterActivator tenantFilterActivator) {
        FilterRegistrationBean<TenantResolutionFilter> registration =
                new FilterRegistrationBean<>(new TenantResolutionFilter(tenantFilterActivator));
        // Se ejecuta tras la cadena de Spring Security (orden -100).
        registration.setOrder(TENANT_FILTER_ORDER);
        registration.addUrlPatterns("/*");
        registration.setName("tenantResolutionFilter");
        return registration;
    }
}
