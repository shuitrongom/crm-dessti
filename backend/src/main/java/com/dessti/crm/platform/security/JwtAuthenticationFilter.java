package com.dessti.crm.platform.security;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dessti.crm.platform.security.jwt.ClaimsToken;
import com.dessti.crm.platform.security.jwt.ServicioTokensJwt;
import com.dessti.crm.platform.security.jwt.TokenInvalidoException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro que autentica las peticiones a partir del {@code Token_Acceso} JWT
 * presentado en la cabecera {@code Authorization: Bearer <token>} (Req 1.6).
 *
 * <p>Si el token es valido, construye un {@link UsuarioAutenticado} como
 * principal y lo coloca en el {@code SecurityContext} junto con las autoridades
 * derivadas de roles y permisos. Si no hay token, deja pasar la peticion sin
 * autenticar (los endpoints protegidos la rechazaran con 401 mas adelante); si
 * el token es invalido/expirado, tampoco autentica (el punto de entrada
 * responde 401, Req 1.6).</p>
 *
 * <p><strong>Orden (integracion tarea 4.1):</strong> este filtro forma parte de
 * la cadena de Spring Security, que se ejecuta ANTES del
 * {@code TenantResolutionFilter} (registrado con orden 0, posterior a la cadena
 * de seguridad en -100). Asi, cuando el {@code TenantResolutionFilter} corre,
 * el principal {@link UsuarioAutenticado} (que implementa {@code TenantAware})
 * ya esta disponible y su {@code tenant_id} se fija en el {@code TenantContext}.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String CABECERA = "Authorization";
    private static final String PREFIJO_BEARER = "Bearer ";

    /** Prefijo de autoridad para roles, convencion de Spring Security. */
    private static final String PREFIJO_ROL = "ROLE_";

    private final ServicioTokensJwt servicioTokens;

    public JwtAuthenticationFilter(ServicioTokensJwt servicioTokens) {
        this.servicioTokens = servicioTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String token = extraerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                ClaimsToken claims = servicioTokens.validarTokenAcceso(token);
                autenticar(request, claims);
            } catch (TokenInvalidoException ex) {
                // No se autentica; el EntryPoint respondera 401 en endpoints
                // protegidos (Req 1.6). No se filtra el detalle al cliente.
                if (log.isDebugEnabled()) {
                    log.debug("Token de acceso rechazado: {}", ex.getMessage());
                }
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void autenticar(HttpServletRequest request, ClaimsToken claims) {
        UsuarioAutenticado principal = new UsuarioAutenticado(claims.subject(), claims.tenantId());

        List<SimpleGrantedAuthority> autoridades = Stream.concat(
                claims.roles().stream().map(r -> new SimpleGrantedAuthority(PREFIJO_ROL + r)),
                claims.permisos().stream().map(SimpleGrantedAuthority::new)
        ).toList();

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, autoridades);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String extraerToken(HttpServletRequest request) {
        String cabecera = request.getHeader(CABECERA);
        if (cabecera != null && cabecera.startsWith(PREFIJO_BEARER)) {
            String valor = cabecera.substring(PREFIJO_BEARER.length()).trim();
            return valor.isEmpty() ? null : valor;
        }
        return null;
    }
}
