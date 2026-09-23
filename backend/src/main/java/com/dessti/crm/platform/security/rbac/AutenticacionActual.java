package com.dessti.crm.platform.security.rbac;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilidad de acceso al {@link Authentication} vigente del hilo, con la
 * politica de <strong>denegacion por defecto</strong> del RBAC (Req 3).
 *
 * <p>Encapsula la lectura del {@link SecurityContextHolder} y considera valido
 * unicamente a un principal <em>autenticado y no anonimo</em>. Cualquier otro
 * caso (sin contexto, no autenticado o autenticacion anonima) se trata como
 * ausencia de identidad, lo que conduce a la denegacion.</p>
 */
public final class AutenticacionActual {

    private AutenticacionActual() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Devuelve el {@link Authentication} vigente si corresponde a un Usuario
     * autenticado real.
     *
     * @return el {@link Authentication} autenticado y no anonimo, o vacio.
     */
    public static Optional<Authentication> obtener() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(authentication);
    }
}
