package com.dessti.crm.platform.security.ratelimit;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.dessti.crm.platform.error.LimiteSolicitudesExcedidoException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro de <b>limitacion de tasa por IP</b> para los endpoints de
 * autenticacion (Req 2.4).
 *
 * <p>Ante cada peticion resuelve la IP de origen (considerando
 * {@code X-Forwarded-For} porque IIS actua como Proxy_Inverso) y consulta el
 * {@link LimitadorTasaPorIp}. Si la IP supera el limite (100 req/min por
 * defecto), la peticion se rechaza <b>sin procesarse</b> con HTTP 429.</p>
 *
 * <p><strong>Formato uniforme del error:</strong> en lugar de escribir un
 * cuerpo ad-hoc, se delega la excepcion {@link LimiteSolicitudesExcedidoException}
 * al {@link HandlerExceptionResolver} de Spring MVC, que la enruta al
 * {@code ManejadorGlobalErrores} y produce el mismo cuerpo Problem Details
 * (RFC 7807) que el resto de errores de la API (mismo namespace de {@code type}
 * y {@code traceId} del MDC). Ademas se agrega la cabecera {@code Retry-After}
 * (en segundos, aproximada a la duracion de la ventana).</p>
 *
 * <p><strong>Alcance:</strong> este filtro se registra unicamente para las
 * rutas de autenticacion (ver {@code RateLimitConfig}) para acotar el coste; el
 * limitador subyacente es por instancia (single-node), como documenta
 * {@link LimitadorTasaPorIp}.</p>
 */
public class FiltroLimiteTasa extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltroLimiteTasa.class);

    private static final String MSG_LIMITE =
            "Se supero el limite de solicitudes permitidas. Intente de nuevo mas tarde.";
    private static final String HEADER_RETRY_AFTER = "Retry-After";

    private final LimitadorTasaPorIp limitador;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final long retryAfterSegundos;

    /**
     * @param limitador                limitador de tasa por IP.
     * @param handlerExceptionResolver resolutor de excepciones de Spring MVC
     *                                 (bean {@code handlerExceptionResolver})
     *                                 para formatear el 429 como Problem Details.
     * @param retryAfterSegundos       valor sugerido de la cabecera
     *                                 {@code Retry-After}.
     */
    public FiltroLimiteTasa(LimitadorTasaPorIp limitador,
                            HandlerExceptionResolver handlerExceptionResolver,
                            long retryAfterSegundos) {
        this.limitador = limitador;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.retryAfterSegundos = retryAfterSegundos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String ip = ResolvedorIpCliente.resolver(request);

        if (!limitador.permitir(ip)) {
            if (log.isWarnEnabled()) {
                // Se registra el hecho sin exponer la IP completa en un nivel
                // superior; el detalle tecnico queda en el log del servidor.
                log.warn("Limite de tasa superado para una direccion de origen en {}",
                        request.getRequestURI());
            }
            response.setHeader(HEADER_RETRY_AFTER, Long.toString(retryAfterSegundos));
            handlerExceptionResolver.resolveException(request, response, null,
                    new LimiteSolicitudesExcedidoException(MSG_LIMITE));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
