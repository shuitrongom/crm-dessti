package com.dessti.crm.platform.security.ratelimit;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Configuracion del limitador de tasa por IP (Req 2.4).
 *
 * <p>Registra el {@link LimitadorTasaPorIp} (respaldado por el {@link Clock}
 * comun de la aplicacion, para determinismo en pruebas) y el
 * {@link FiltroLimiteTasa} acotado a las rutas de autenticacion
 * ({@code /auth/*}). El filtro se ordena <b>despues</b> del filtro de trace
 * (para conservar el {@code traceId} en el cuerpo del error) y antes de la
 * cadena de seguridad y del despacho MVC.</p>
 */
@Configuration
@EnableConfigurationProperties(LimiteTasaProperties.class)
public class RateLimitConfig {

    /**
     * Orden del filtro de rate limiting: tras el filtro de trace
     * ({@code HIGHEST_PRECEDENCE}) y antes de la cadena de Spring Security
     * (orden {@code -100}), para rechazar el exceso lo antes posible sin perder
     * el {@code traceId} de correlacion.
     */
    public static final int RATE_LIMIT_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    public LimitadorTasaPorIp limitadorTasaPorIp(LimiteTasaProperties propiedades, Clock clock) {
        return new LimitadorTasaPorIp(
                propiedades.maxPeticiones(), propiedades.ventanaMillis(), clock);
    }

    @Bean
    public FilterRegistrationBean<FiltroLimiteTasa> filtroLimiteTasaRegistration(
            LimitadorTasaPorIp limitadorTasaPorIp,
            LimiteTasaProperties propiedades,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        FiltroLimiteTasa filtro = new FiltroLimiteTasa(
                limitadorTasaPorIp, handlerExceptionResolver, propiedades.ventanaSegundos());
        FilterRegistrationBean<FiltroLimiteTasa> registration =
                new FilterRegistrationBean<>(filtro);
        registration.setOrder(RATE_LIMIT_FILTER_ORDER);
        // Acotado a las rutas de autenticacion (Req 2.4: "al menos /auth/login").
        // El context-path /api/v1 lo agrega el contenedor; los url-patterns del
        // filtro son relativos a el.
        registration.addUrlPatterns("/auth/*");
        registration.setName("filtroLimiteTasa");
        return registration;
    }
}
