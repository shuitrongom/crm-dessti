package com.dessti.crm.platform.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Configuracion de infraestructura web transversal de observabilidad.
 *
 * <p>Registra el {@link TraceIdFilter} con la <strong>maxima precedencia</strong>
 * para que el {@code traceId} de correlacion (Req 10.12) este disponible en el
 * MDC durante toda la peticion, incluidos los filtros de seguridad y de
 * resolucion de tenant y, por tanto, cualquier registro de auditoria que se
 * produzca en la peticion.</p>
 */
@Configuration
public class WebObservabilidadConfig {

    /** Orden del filtro de trace: el primero de la cadena. */
    public static final int TRACE_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE;

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(TRACE_FILTER_ORDER);
        registration.addUrlPatterns("/*");
        registration.setName("traceIdFilter");
        return registration;
    }
}
