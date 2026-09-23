package com.dessti.crm.platform.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

/**
 * Servicio de emision y validacion de tokens JWT firmados con HMAC-SHA256
 * (Req 1). Es una pieza <b>pura</b> (sin dependencias de Spring MVC ni de la
 * base de datos), lo que la hace directamente testeable por unidad.
 *
 * <p><strong>Firma (Req 11):</strong> la clave HMAC proviene de
 * {@code SecretosProperties#jwtSigningKey()} y se inyecta como cadena. NUNCA se
 * registra en logs ni se expone. Debe tener al menos 32 bytes (256 bits) para
 * HS256; una clave debil detiene la construccion del servicio (fail-fast).</p>
 *
 * <p><strong>Vigencias (Req 1.4, 1.7):</strong> el Token_Acceso no excede 15
 * minutos y el Token_Refresco no excede 7 dias. Estos limites se validan al
 * construir el servicio a partir de {@link JwtProperties}.</p>
 *
 * <p><strong>Reloj:</strong> se inyecta un {@link Clock} para permitir pruebas
 * deterministas de vigencia y expiracion.</p>
 */
public class ServicioTokensJwt {

    /** Claim con el identificador de la empresa (Req 23.4). */
    public static final String CLAIM_TENANT_ID = "tenant_id";

    /** Claim con la lista de roles del Usuario. */
    public static final String CLAIM_ROLES = "roles";

    /** Claim con la lista de permisos atomicos del Usuario. */
    public static final String CLAIM_PERMISOS = "permisos";

    /** Claim con el tipo de token ({@link TipoToken}). */
    public static final String CLAIM_TIPO = "typ";

    /**
     * Claim con la <b>clave canonica del Giro</b> de la Empresa del Usuario
     * (p. ej. {@code anuncios-luminosos}), tarea 10.1 / Req 9.1.
     *
     * <p>Permite que el contexto de sesion del frontend conozca el Giro sin un
     * endpoint adicional: el frontend ya decodifica el JWT para roles, permisos
     * y {@code tenant_id}, de modo que el Giro viaja por el mismo canal.</p>
     *
     * <p><strong>Ausencia coherente con {@code tenant_id}:</strong> un principal
     * de plataforma (super_admin, {@code tenant_id} nulo) NO pertenece a ninguna
     * Empresa y por tanto NO tiene Giro; en ese caso el claim se <b>omite</b>,
     * igual que se omite {@code tenant_id}.</p>
     */
    public static final String CLAIM_GIRO = "giro";

    /**
     * Claim con el <b>identificador de acceso legible</b> del Usuario (p. ej.
     * {@code superadmin@dessti}).
     *
     * <p>El {@code sub} del token es el {@code id} (UUID) del Usuario, usado
     * internamente para sesiones/refresco y NUNCA legible para una persona. Para
     * que el frontend pueda mostrar el login real en el menu de cuenta sin un
     * endpoint adicional, el identificador legible viaja como claim propio, por
     * el mismo canal que {@code roles}, {@code permisos}, {@code tenant_id} y
     * {@code giro}.</p>
     *
     * <p><strong>Omision coherente:</strong> si el identificador es
     * {@code null}/vacio el claim se <b>omite</b>, igual que se omite
     * {@code giro} para el principal de plataforma.</p>
     */
    public static final String CLAIM_IDENTIFICADOR = "identificador";

    /**
     * Claim con la <b>lista de claves canonicas de modulos habilitados</b> para
     * la Empresa del Usuario (p. ej. {@code ["comercial","facturacion"]}),
     * derivada de su Suscripcion activa (Req 25.4).
     *
     * <p>Permite que el frontend pinte el menu <strong>solo</strong> con los
     * modulos contratados sin un endpoint adicional: el JWT ya se decodifica para
     * roles, permisos, {@code tenant_id} y {@code giro}, de modo que los modulos
     * viajan por el mismo canal.</p>
     *
     * <p><strong>Semantica de presencia (coherente con el gating):</strong></p>
     * <ul>
     *   <li>Un principal de plataforma (super_admin, {@code tenant_id} nulo) NO
     *       se rige por el gating de modulos: se pasa {@code null} y el claim se
     *       <b>omite</b>, igual que {@code tenant_id} y {@code giro}.</li>
     *   <li>Una Empresa sin ningun modulo habilitado (p. ej. override vacio)
     *       lleva el claim presente pero <b>vacio</b> ({@code []}): cero modulos,
     *       distinto de "sin restriccion".</li>
     * </ul>
     */
    public static final String CLAIM_MODULOS = "modulos";

    private final SecretKey clave;
    private final JwtProperties propiedades;
    private final Clock clock;

    /**
     * @param claveFirma  clave de firma HMAC (origen: {@code SecretosProperties}, Req 11).
     * @param propiedades vigencias y emisor de los tokens.
     * @param clock       reloj para calcular {@code iat}/{@code exp} (inyectable en pruebas).
     * @throws IllegalArgumentException si la clave es debil o las vigencias
     *         exceden los limites de norma (Req 1.4, 1.7).
     */
    public ServicioTokensJwt(String claveFirma, JwtProperties propiedades, Clock clock) {
        this.propiedades = propiedades;
        this.clock = clock;
        this.clave = construirClave(claveFirma);
        validarVigencias(propiedades);
    }

    private static SecretKey construirClave(String claveFirma) {
        if (claveFirma == null || claveFirma.isBlank()) {
            // No se incluye el valor (inexistente) en el mensaje (Req 11.3).
            throw new IllegalArgumentException("La clave de firma JWT no puede estar vacia");
        }
        try {
            return Keys.hmacShaKeyFor(claveFirma.getBytes(StandardCharsets.UTF_8));
        } catch (WeakKeyException ex) {
            // No se registra el valor de la clave (Req 11.3).
            throw new IllegalArgumentException(
                    "La clave de firma JWT es demasiado corta; se requieren al menos 256 bits para HS256");
        }
    }

    private static void validarVigencias(JwtProperties propiedades) {
        if (propiedades.vigenciaTokenAcceso().compareTo(JwtProperties.MAX_VIGENCIA_ACCESO) > 0) {
            throw new IllegalArgumentException(
                    "La vigencia del Token_Acceso no puede exceder 15 minutos (Req 1.4)");
        }
        if (propiedades.vigenciaTokenRefresco().compareTo(JwtProperties.MAX_VIGENCIA_REFRESCO) > 0) {
            throw new IllegalArgumentException(
                    "La vigencia del Token_Refresco no puede exceder 7 dias (Req 1.7)");
        }
    }

    /**
     * Emite un {@code Token_Acceso} de vida corta con los claims de identidad y
     * autorizacion (Req 1.4).
     *
     * @param subject   identificador del Usuario (claim {@code sub}).
     * @param tenantId  empresa del Usuario; {@code null} para super_admin.
     * @param roles     roles del Usuario.
     * @param permisos  permisos atomicos del Usuario.
     * @param giro      clave canonica del Giro de la Empresa (claim
     *                  {@code giro}, Req 9.1); {@code null}/vacio para el
     *                  principal de plataforma (super_admin), en cuyo caso el
     *                  claim se omite.
     * @param identificador identificador de acceso legible del Usuario (claim
     *                  {@code identificador}); {@code null}/vacio omite el claim.
     * @param modulos   claves de modulos habilitados de la Empresa (claim
     *                  {@code modulos}, Req 25.4); {@code null} para super_admin
     *                  (claim omitido). Una lista vacia emite {@code []} (cero
     *                  modulos habilitados).
     * @return el token firmado y su instante de expiracion.
     */
    public TokenEmitido emitirTokenAcceso(String subject, UUID tenantId,
                                          List<String> roles, List<String> permisos,
                                          String giro, String identificador,
                                          List<String> modulos) {
        return emitir(subject, tenantId, roles, permisos, giro, identificador, modulos,
                TipoToken.ACCESO, propiedades.vigenciaTokenAcceso());
    }

    /**
     * Emite un {@code Token_Refresco} de vida mas larga (Req 1.7). Incluye los
     * mismos claims de identidad —incluida la clave de {@code giro} (Req 9.1)—
     * para poder reconstruir el Token_Acceso en el refresco sin acceder a la
     * base de datos.
     *
     * @param giro clave canonica del Giro de la Empresa (claim {@code giro});
     *             {@code null}/vacio para super_admin (claim omitido).
     * @param identificador identificador de acceso legible del Usuario (claim
     *             {@code identificador}); se incluye tambien en el refresco para
     *             preservarlo al reemitir el Token_Acceso sin acceder a la BD.
     * @param modulos claves de modulos habilitados de la Empresa (claim
     *             {@code modulos}); se incluye tambien en el refresco para
     *             preservarlo al reemitir el Token_Acceso sin acceder a la BD,
     *             igual que {@code giro}. {@code null} para super_admin.
     */
    public TokenEmitido emitirTokenRefresco(String subject, UUID tenantId,
                                            List<String> roles, List<String> permisos,
                                            String giro, String identificador,
                                            List<String> modulos) {
        return emitir(subject, tenantId, roles, permisos, giro, identificador, modulos,
                TipoToken.REFRESCO, propiedades.vigenciaTokenRefresco());
    }

    private TokenEmitido emitir(String subject, UUID tenantId, List<String> roles,
                                List<String> permisos, String giro, String identificador,
                                List<String> modulos, TipoToken tipo, Duration vigencia) {
        Instant ahora = clock.instant();
        Instant exp = ahora.plus(vigencia);
        // Identificador unico del token (claim jti, RFC 7519). Sustenta el
        // registro/denylist de sesiones y la revocacion de Token_Refresco
        // (Req 68). Se asigna a TODO token (acceso y refresco).
        String jti = UUID.randomUUID().toString();
        var builder = Jwts.builder()
                .id(jti)
                .issuer(propiedades.emisor())
                .subject(subject)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(exp))
                .claim(CLAIM_TIPO, tipo.name())
                .claim(CLAIM_ROLES, roles == null ? List.of() : roles)
                .claim(CLAIM_PERMISOS, permisos == null ? List.of() : permisos);
        // Un tenant nulo (super_admin) no incluye el claim tenant_id.
        if (tenantId != null) {
            builder.claim(CLAIM_TENANT_ID, tenantId.toString());
        }
        // El Giro solo aplica a Usuarios de una Empresa: si es nulo/vacio
        // (super_admin / plataforma) el claim se omite, de forma coherente con
        // el manejo de tenant_id nulo (Req 9.1).
        if (giro != null && !giro.isBlank()) {
            builder.claim(CLAIM_GIRO, giro);
        }
        // El identificador de acceso legible viaja en AMBOS tokens (acceso y
        // refresco) para que el refresco lo preserve al reemitir sin BD. Un
        // valor nulo/vacio omite el claim, de forma coherente con giro.
        if (identificador != null && !identificador.isBlank()) {
            builder.claim(CLAIM_IDENTIFICADOR, identificador);
        }
        // Los modulos habilitados solo aplican a Usuarios de una Empresa: si es
        // nulo (super_admin / plataforma) el claim se omite, de forma coherente
        // con tenant_id y giro. Una lista vacia SI se emite ([]), representando
        // cero modulos habilitados (distinto de "sin restriccion") (Req 25.4).
        if (modulos != null) {
            builder.claim(CLAIM_MODULOS, modulos);
        }
        return new TokenEmitido(builder.signWith(clave).compact(), exp, jti);
    }

    /**
     * Valida la firma y la vigencia de un token de <b>acceso</b> y devuelve sus
     * claims (Req 1.6). Rechaza tokens de refresco presentados como acceso.
     *
     * @throws TokenInvalidoException si el token es invalido, expiro o no es de
     *         tipo acceso.
     */
    public ClaimsToken validarTokenAcceso(String token) {
        return validar(token, TipoToken.ACCESO);
    }

    /**
     * Valida la firma y la vigencia de un token de <b>refresco</b> y devuelve
     * sus claims (Req 1.9). Rechaza tokens de acceso presentados como refresco.
     *
     * @throws TokenInvalidoException si el token es invalido, expiro o no es de
     *         tipo refresco.
     */
    public ClaimsToken validarTokenRefresco(String token) {
        return validar(token, TipoToken.REFRESCO);
    }

    private ClaimsToken validar(String token, TipoToken tipoEsperado) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException("Token ausente");
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(clave)
                    .clock(() -> Date.from(clock.instant()))
                    .requireIssuer(propiedades.emisor())
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();

            TipoToken tipo = leerTipo(claims);
            if (tipo != tipoEsperado) {
                throw new TokenInvalidoException("Tipo de token inesperado");
            }
            return new ClaimsToken(
                    claims.getSubject(),
                    leerTenant(claims),
                    leerLista(claims, CLAIM_ROLES),
                    leerLista(claims, CLAIM_PERMISOS),
                    tipo,
                    claims.getExpiration() != null ? claims.getExpiration().toInstant() : null,
                    claims.getId(),
                    leerGiro(claims),
                    leerIdentificador(claims),
                    leerLista(claims, CLAIM_MODULOS));
        } catch (ExpiredJwtException ex) {
            throw new TokenInvalidoException("Token expirado", ex);
        } catch (JwtException | IllegalArgumentException ex) {
            // Firma invalida, formato incorrecto, emisor no coincide, etc.
            throw new TokenInvalidoException("Token invalido", ex);
        }
    }

    private static TipoToken leerTipo(Claims claims) {
        Object valor = claims.get(CLAIM_TIPO);
        if (valor == null) {
            throw new TokenInvalidoException("Token sin tipo");
        }
        try {
            return TipoToken.valueOf(valor.toString());
        } catch (IllegalArgumentException ex) {
            throw new TokenInvalidoException("Tipo de token desconocido", ex);
        }
    }

    private static UUID leerTenant(Claims claims) {
        Object valor = claims.get(CLAIM_TENANT_ID);
        if (valor == null) {
            return null; // principal de plataforma (super_admin)
        }
        try {
            return UUID.fromString(valor.toString());
        } catch (IllegalArgumentException ex) {
            throw new TokenInvalidoException("tenant_id invalido en el token", ex);
        }
    }

    /**
     * Lee la clave de {@code giro} del token (Req 9.1). Devuelve {@code null}
     * cuando el claim esta ausente (super_admin / plataforma) o vacio, de forma
     * coherente con {@link #leerTenant(Claims)}.
     */
    private static String leerGiro(Claims claims) {
        Object valor = claims.get(CLAIM_GIRO);
        if (valor == null) {
            return null; // principal de plataforma o token sin Giro
        }
        String clave = valor.toString();
        return clave.isBlank() ? null : clave;
    }

    /**
     * Lee el {@code identificador} de acceso legible del token. Devuelve
     * {@code null} cuando el claim esta ausente o vacio, de forma coherente con
     * {@link #leerGiro(Claims)}.
     */
    private static String leerIdentificador(Claims claims) {
        Object valor = claims.get(CLAIM_IDENTIFICADOR);
        if (valor == null) {
            return null;
        }
        String identificador = valor.toString();
        return identificador.isBlank() ? null : identificador;
    }

    private static List<String> leerLista(Claims claims, String nombre) {
        Object valor = claims.get(nombre);
        if (valor instanceof List<?> lista) {
            return lista.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
