package com.dessti.crm.portalcliente.adapter.out.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.portalcliente.application.ClientePortalActualPort;

/**
 * Implementacion por defecto del {@link ClientePortalActualPort} que resuelve el
 * Cliente del usuario del Portal a partir de las <em>authorities</em> del
 * {@link Authentication} vigente (Req 45.1, 45.3).
 *
 * <h2>Como se resuelve</h2>
 * <p>Busca entre las authorities del principal una con el prefijo
 * {@value #PREFIJO_CLIENTE} seguida de un UUID valido (por ejemplo
 * {@code cliente_id:6f9619ff-8b86-d011-b42d-00cf4fc964ff}). Esa authority la
 * emite la capa de autenticacion en el {@code Token_Acceso} del usuario del Portal
 * (rol {@code cliente_portal}). Al leerse del token en cada peticion, la
 * resolucion es intrinsecamente <em>intra-tenant</em>: el {@code tenant_id} del
 * mismo token ya fijo el {@code TenantContext} (Req 23.4).</p>
 *
 * <h2>Denegacion por defecto (Req 3, 45.3)</h2>
 * <p>Si no hay un principal autenticado real, o no porta una authority
 * {@value #PREFIJO_CLIENTE} con un UUID valido, se lanza
 * {@link AccessDeniedException} (que el manejador global traduce a 403). De este
 * modo el Portal nunca ejecuta una consulta sin un Cliente resuelto y jamas revela
 * datos de otros Clientes.</p>
 *
 * <h2>Sustituibilidad</h2>
 * <p>Se registra mediante un metodo {@code @Bean} {@code @ConditionalOnMissingBean}
 * en una clase {@code @Configuration} ({@link PortalClienteSeguridadConfig}): cuando
 * se introduzca un mecanismo definitivo de vinculo Usuario -&gt; Cliente (claim
 * dedicado en el JWT o tabla de vinculo), bastara aportar otro bean
 * {@link ClientePortalActualPort} y este adaptador dejara de registrarse, sin tocar
 * el resto del Portal.</p>
 */
public class ClientePortalActualDesdeAuthenticationAdapter implements ClientePortalActualPort {

    /** Prefijo de la authority que porta el identificador del Cliente del Portal. */
    static final String PREFIJO_CLIENTE = "cliente_id:";

    @Override
    public UUID clienteIdActual() {
        Authentication autenticacion = AutenticacionActual.obtener()
                .orElseThrow(() -> new AccessDeniedException(
                        "Se requiere un usuario del Portal autenticado."));
        for (GrantedAuthority authority : autenticacion.getAuthorities()) {
            String valor = authority.getAuthority();
            if (valor != null && valor.startsWith(PREFIJO_CLIENTE)) {
                return interpretar(valor.substring(PREFIJO_CLIENTE.length()));
            }
        }
        throw new AccessDeniedException(
                "El usuario del Portal no tiene un Cliente asociado resoluble.");
    }

    /**
     * Interpreta el sufijo de la authority como un UUID de Cliente. Un valor mal
     * formado se trata como ausencia de identidad (denegacion, 403), nunca como un
     * Cliente arbitrario.
     */
    private static UUID interpretar(String posibleUuid) {
        try {
            return UUID.fromString(posibleUuid.strip());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException(
                    "El Cliente asociado al usuario del Portal no es valido.");
        }
    }
}
