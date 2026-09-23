package com.dessti.crm.platform.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro de servlet que resuelve el {@code tenant_id} de la peticion en curso a
 * partir del <strong>contexto autenticado</strong> y lo coloca en el
 * {@link TenantContext} (Capa 1 del aislamiento multi-tenant, Req 23).
 *
 * <p><strong>Orden:</strong> este filtro se ejecuta <em>despues</em> de la
 * cadena de seguridad de Spring Security, de modo que el {@link Authentication}
 * ya este disponible cuando se resuelve el tenant. El registro y el orden se
 * configuran en {@link TenantWebConfig}.</p>
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> el {@code tenant_id} se
 * deriva exclusivamente del principal autenticado. <em>Nunca</em> se toma de
 * parametros de la peticion (query, path, cabeceras arbitrarias o cuerpo).</p>
 *
 * <p><strong>TODO (tarea 9 - JWT):</strong> la autenticacion basada en JWT aun
 * no esta implementada. Este filtro deja preparado el punto de extraccion: si
 * hay un {@link Authentication} cuyo principal expone un {@code tenant_id}
 * (a traves de {@link TenantAware}), se usa dicho valor. Cuando la tarea 9
 * emita los JWT con el claim {@code tenant_id}, bastara con que el principal
 * resultante implemente {@link TenantAware} (o adaptar aqui la extraccion del
 * claim) sin cambiar el resto de la infraestructura. Mientras tanto, si no hay
 * tenant resoluble, el contexto queda vacio y las operaciones que lo requieran
 * fallaran de forma controlada via {@link TenantContext#require()}.</p>
 */
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantResolutionFilter.class);

    private final TenantFilterActivator tenantFilterActivator;

    public TenantResolutionFilter(TenantFilterActivator tenantFilterActivator) {
        this.tenantFilterActivator = tenantFilterActivator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            resolveTenantFromAuthentication().ifPresent(tenantId -> {
                TenantContext.set(tenantId);
                // Habilita el filtro de Hibernate para esta peticion (Capa 1).
                tenantFilterActivator.enableForCurrentTenant();
            });
            filterChain.doFilter(request, response);
        } finally {
            // Limpieza obligatoria para no filtrar el tenant a peticiones que
            // reutilicen el mismo hilo del contenedor.
            TenantContext.clear();
        }
    }

    /**
     * Extrae el {@code tenant_id} del contexto autenticado, si existe.
     *
     * <p>Estrategia de extraccion (preparada para la tarea 9):</p>
     * <ol>
     *   <li>Si no hay {@link Authentication} autenticado, no hay tenant.</li>
     *   <li>Si el principal implementa {@link TenantAware}, se usa su tenant.</li>
     *   <li>En otro caso (por ahora), no se resuelve tenant.</li>
     * </ol>
     */
    private java.util.Optional<UUID> resolveTenantFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return java.util.Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof TenantAware tenantAware && tenantAware.getTenantId() != null) {
            return java.util.Optional.of(tenantAware.getTenantId());
        }

        // TODO (tarea 9): extraer el claim "tenant_id" del JWT cuando la
        // autenticacion JWT este implementada. No se lee de la peticion (Req 23.4).
        if (log.isTraceEnabled()) {
            log.trace("Sin tenant resoluble desde el principal autenticado; "
                    + "pendiente integracion JWT (tarea 9)");
        }
        return java.util.Optional.empty();
    }
}
