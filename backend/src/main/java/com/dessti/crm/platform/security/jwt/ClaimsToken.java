package com.dessti.crm.platform.security.jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Claims relevantes extraidos de un JWT validado (Req 1).
 *
 * <p>Representa de forma tipada la informacion util del token una vez que su
 * firma y vigencia han sido verificadas por {@link ServicioTokensJwt}.</p>
 *
 * @param subject   claim {@code sub}: identificador del Usuario.
 * @param tenantId  claim {@code tenant_id}: empresa del Usuario; {@code null}
 *                  para principales de plataforma (super_admin).
 * @param roles     roles del Usuario (claim {@code roles}).
 * @param permisos  permisos atomicos del Usuario (claim {@code permisos}).
 * @param tipo      tipo del token ({@link TipoToken}).
 * @param expiracion instante de expiracion ({@code exp}).
 * @param jti       claim {@code jti}: identificador unico del token. Permite
 *                  consultar la denylist/registro de sesiones y revocar el
 *                  Token_Refresco (Req 68); puede ser {@code null} en tokens
 *                  emitidos sin identificador.
 * @param giro      claim {@code giro}: <b>clave canonica del Giro</b> de la
 *                  Empresa del Usuario (p. ej. {@code anuncios-luminosos}),
 *                  tarea 10.1 / Req 9.1. Es {@code null} para principales de
 *                  plataforma (super_admin, {@code tenantId} nulo), que no
 *                  pertenecen a ninguna Empresa y por tanto no tienen Giro.
 * @param identificador claim {@code identificador}: identificador de acceso
 *                  <b>legible</b> del Usuario (p. ej. {@code superadmin@dessti}).
 *                  A diferencia de {@code subject} (el UUID interno), es el login
 *                  que el frontend muestra en el menu de cuenta. Es {@code null}
 *                  cuando el claim esta ausente (token emitido sin el).
 * @param modulos   claim {@code modulos}: <b>claves canonicas de los modulos
 *                  habilitados</b> para la Empresa del Usuario (Req 25.4). El
 *                  frontend lo usa para pintar el menu solo con lo contratado.
 *                  Cuando el claim esta ausente (super_admin / token sin el) se
 *                  normaliza a la lista <em>vacia</em>; una Empresa sin modulos
 *                  tambien lleva la lista vacia.
 */
public record ClaimsToken(
        String subject,
        UUID tenantId,
        List<String> roles,
        List<String> permisos,
        TipoToken tipo,
        Instant expiracion,
        String jti,
        String giro,
        String identificador,
        List<String> modulos
) {

    public ClaimsToken {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permisos = permisos == null ? List.of() : List.copyOf(permisos);
        modulos = modulos == null ? List.of() : List.copyOf(modulos);
    }

    /** @return {@code true} si el token es un {@link TipoToken#ACCESO}. */
    public boolean esAcceso() {
        return tipo == TipoToken.ACCESO;
    }

    /** @return {@code true} si el token es un {@link TipoToken#REFRESCO}. */
    public boolean esRefresco() {
        return tipo == TipoToken.REFRESCO;
    }
}
