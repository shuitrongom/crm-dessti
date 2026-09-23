package com.dessti.crm.platform.web;

import java.io.IOException;
import java.util.UUID;

import com.dessti.crm.platform.audit.ServicioAuditoria;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro ligero que establece un {@code traceId} de correlacion por peticion en
 * el {@link MDC} de logging (Req 10.12), para trazabilidad extremo a extremo.
 *
 * <p>Reglas:</p>
 * <ul>
 *   <li>Si la peticion trae un identificador de correlacion en la cabecera
 *       {@value #HEADER_TRACE_ID} (propagado por el Proxy_Inverso u otro
 *       servicio aguas arriba), se reutiliza tras sanitizarlo.</li>
 *   <li>En caso contrario se genera un UUID nuevo.</li>
 *   <li>El valor se expone tambien en la respuesta ({@value #HEADER_TRACE_ID})
 *       para correlacionar cliente y servidor.</li>
 *   <li>El MDC se <strong>limpia siempre</strong> al finalizar para no filtrar
 *       el trace a peticiones que reutilicen el hilo del contenedor.</li>
 * </ul>
 *
 * <p>Usa la misma clave de MDC que lee {@link ServicioAuditoria} al registrar
 * eventos ({@link ServicioAuditoria#MDC_TRACE_ID}), de modo que cada
 * {@code RegistroAuditoria} queda correlacionado con la peticion que lo
 * origino.</p>
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /** Cabecera de correlacion entrante/saliente. */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** Longitud maxima aceptada para un trace entrante (coincide con la BD). */
    private static final int MAX_LONGITUD = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolver(request.getHeader(HEADER_TRACE_ID));
        MDC.put(ServicioAuditoria.MDC_TRACE_ID, traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(ServicioAuditoria.MDC_TRACE_ID);
        }
    }

    /**
     * Reutiliza el trace entrante si es valido; en otro caso genera uno nuevo.
     * Sanitiza el valor entrante (longitud y caracteres) para no admitir datos
     * arbitrarios en el MDC ni en la cabecera de respuesta.
     */
    private String resolver(String entrante) {
        if (entrante == null) {
            return nuevo();
        }
        String limpio = entrante.trim();
        if (limpio.isEmpty() || limpio.length() > MAX_LONGITUD || !esSeguro(limpio)) {
            return nuevo();
        }
        return limpio;
    }

    private static boolean esSeguro(String valor) {
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            boolean permitido = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!permitido) {
                return false;
            }
        }
        return true;
    }

    private static String nuevo() {
        return UUID.randomUUID().toString();
    }
}
