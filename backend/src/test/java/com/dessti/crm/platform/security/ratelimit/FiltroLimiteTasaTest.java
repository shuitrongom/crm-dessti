package com.dessti.crm.platform.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.dessti.crm.platform.error.LimiteSolicitudesExcedidoException;

import jakarta.servlet.FilterChain;

/**
 * Pruebas unitarias del {@link FiltroLimiteTasa} (Req 2.4): al exceder el
 * limite, la peticion no continua por la cadena y se delega un 429 al
 * resolutor de excepciones de Spring MVC; ademas resuelve la IP considerando
 * {@code X-Forwarded-For}.
 */
class FiltroLimiteTasaTest {

    private LimitadorTasaPorIp limitador;
    private HandlerExceptionResolver resolver;
    private FiltroLimiteTasa filtro;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        // Limite de 1 peticion/min para forzar el rechazo en la segunda.
        limitador = new LimitadorTasaPorIp(1, 60_000L, reloj);
        resolver = mock(HandlerExceptionResolver.class);
        filtro = new FiltroLimiteTasa(limitador, resolver, 60L);
    }

    @Test
    @DisplayName("Dentro del limite: la peticion continua por la cadena")
    void dentroDelLimiteContinua() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Al exceder el limite: no continua la cadena y delega un 429 con Retry-After")
    void excedeDelegaError() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // 1a peticion: permitida.
        MockHttpServletRequest r1 = new MockHttpServletRequest("POST", "/auth/login");
        r1.setRemoteAddr("10.0.0.5");
        filtro.doFilter(r1, new MockHttpServletResponse(), chain);

        // 2a peticion desde la misma IP: excede el limite.
        MockHttpServletRequest r2 = new MockHttpServletRequest("POST", "/auth/login");
        r2.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filtro.doFilter(r2, response2, chain);

        // La cadena solo avanzo para la primera peticion.
        verify(chain, times(1)).doFilter(any(), any());
        // Se delego la excepcion de rate limit al resolver de Spring MVC.
        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(resolver).resolveException(eq(r2), eq(response2), isNull(), ex.capture());
        assertThat(ex.getValue()).isInstanceOf(LimiteSolicitudesExcedidoException.class);
        assertThat(response2.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    @DisplayName("Resuelve la IP del primer salto de X-Forwarded-For")
    void resuelveIpDeForwardedFor() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Dos IP distintas via X-Forwarded-For con el mismo remoteAddr (proxy):
        // el limite (1/min) debe aplicarse por IP de cliente, no por el proxy.
        MockHttpServletRequest cliente1 = new MockHttpServletRequest("POST", "/auth/login");
        cliente1.setRemoteAddr("10.0.0.1"); // IP del proxy
        cliente1.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.1");
        filtro.doFilter(cliente1, new MockHttpServletResponse(), chain);

        MockHttpServletRequest cliente2 = new MockHttpServletRequest("POST", "/auth/login");
        cliente2.setRemoteAddr("10.0.0.1"); // mismo proxy
        cliente2.addHeader("X-Forwarded-For", "198.51.100.2, 10.0.0.1");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filtro.doFilter(cliente2, response2, chain);

        // Ambas peticiones (clientes distintos) pasaron: no se delego ningun 429.
        verify(chain, times(2)).doFilter(any(), any());
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }
}
