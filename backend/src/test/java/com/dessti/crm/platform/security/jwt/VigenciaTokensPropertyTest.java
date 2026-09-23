package com.dessti.crm.platform.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 26: Vigencia
 * acotada de los tokens</strong> (Req 1.4, 1.7).
 *
 * <p>Reutiliza la pieza de produccion {@link ServicioTokensJwt} con una clave
 * de firma de PRUEBA (>= 256 bits, HS256) y un {@link Clock} <b>fijo</b> para
 * hacer deterministas los calculos de {@code iat}/{@code exp}. No hay base de
 * datos ni contexto de Spring: el servicio es una pieza pura.</p>
 *
 * <p>La property comprueba universalmente que, para vigencias configuradas
 * <em>dentro de norma</em> (acceso en PT1S..PT15M, refresco en PT1M..P7D) y
 * para identidades arbitrarias (subject, tenantId nulo o UUID, roles y
 * permisos), el instante de expiracion emitido es exactamente
 * {@code ahora + vigenciaConfigurada}, su vigencia {@code exp - ahora} nunca
 * excede el limite de norma (15 min para acceso, 7 dias para refresco) y es
 * estrictamente positiva. Ademas, al validar el token con el mismo servicio,
 * los claims (sub, tenant_id, roles, permisos, tipo) y la expiracion coinciden
 * con lo emitido.</p>
 */
class VigenciaTokensPropertyTest {

    /** Clave de PRUEBA de >= 32 bytes (256 bits) para HS256. No es un secreto real. */
    private static final String CLAVE_PRUEBA = "clave-de-firma-jwt-solo-para-pruebas-0123456789";

    /** Reloj fijo: {@code ahora} es deterministico en todas las iteraciones. */
    private static final Instant AHORA = Instant.parse("2025-01-15T10:00:00Z");
    private static final Clock RELOJ_FIJO = Clock.fixed(AHORA, ZoneOffset.UTC);

    private static final Duration MAX_ACCESO = JwtProperties.MAX_VIGENCIA_ACCESO;   // PT15M
    private static final Duration MAX_REFRESCO = JwtProperties.MAX_VIGENCIA_REFRESCO; // P7D

    private ServicioTokensJwt servicioCon(Duration vigenciaAcceso, Duration vigenciaRefresco) {
        JwtProperties props = new JwtProperties("crm-test", vigenciaAcceso, vigenciaRefresco);
        return new ServicioTokensJwt(CLAVE_PRUEBA, props, RELOJ_FIJO);
    }

    // ----------------------------------------------------------------------
    // Property 26 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 26: Para cualquier Token_Acceso emitido, su vigencia (`exp − iat`) no excede 15 minutos, y para cualquier Token_Refresco emitido, su vigencia no excede 7 días.
    @Property(tries = 1000)
    void vigenciaAccesoAcotadaYExactaAlEmitir(
            @ForAll("subjects") String subject,
            @ForAll("tenants") UUID tenantId,
            @ForAll("autoridades") @Size(max = 5) List<String> roles,
            @ForAll("autoridades") @Size(max = 5) List<String> permisos,
            @ForAll("vigenciasAcceso") Duration vigenciaAcceso) {

        ServicioTokensJwt servicio = servicioCon(vigenciaAcceso, MAX_REFRESCO);

        // El Giro (Req 9.1) es ajeno a la Property 26 (vigencia): se emite con
        // giro null para no alterar la propiedad bajo prueba.
        TokenEmitido emitido = servicio.emitirTokenAcceso(subject, tenantId, roles, permisos, null, null, null);

        // La vigencia configurada nunca excede el limite de norma (precondicion del generador).
        assertThat(vigenciaAcceso)
                .as("la vigencia de acceso configurada debe estar dentro de norma (<= 15 min)")
                .isLessThanOrEqualTo(MAX_ACCESO);

        // exp == ahora + vigenciaConfigurada (reloj fijo => deterministico).
        assertThat(emitido.expiracion())
                .as("la expiracion emitida debe ser ahora + vigenciaConfigurada")
                .isEqualTo(AHORA.plus(vigenciaAcceso));

        Duration ttl = Duration.between(AHORA, emitido.expiracion());
        assertThat(ttl)
                .as("exp - ahora nunca excede 15 minutos")
                .isLessThanOrEqualTo(MAX_ACCESO)
                .as("exp - ahora es estrictamente positiva (exp > ahora)")
                .isPositive();

        // Al validar con el mismo servicio, los claims y la expiracion coinciden.
        ClaimsToken claims = servicio.validarTokenAcceso(emitido.valor());
        assertThat(claims.subject()).isEqualTo(subject);
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.roles()).containsExactlyElementsOf(roles);
        assertThat(claims.permisos()).containsExactlyElementsOf(permisos);
        assertThat(claims.esAcceso()).isTrue();
        // exp del token (segundos) es la misma que la expiracion emitida.
        assertThat(claims.expiracion()).isEqualTo(emitido.expiracion());
    }

    // Feature: crm-anuncios-luminosos, Property 26: Para cualquier Token_Acceso emitido, su vigencia (`exp − iat`) no excede 15 minutos, y para cualquier Token_Refresco emitido, su vigencia no excede 7 días.
    @Property(tries = 1000)
    void vigenciaRefrescoAcotadaYExactaAlEmitir(
            @ForAll("subjects") String subject,
            @ForAll("tenants") UUID tenantId,
            @ForAll("autoridades") @Size(max = 5) List<String> roles,
            @ForAll("autoridades") @Size(max = 5) List<String> permisos,
            @ForAll("vigenciasRefresco") Duration vigenciaRefresco) {

        ServicioTokensJwt servicio = servicioCon(MAX_ACCESO, vigenciaRefresco);

        TokenEmitido emitido = servicio.emitirTokenRefresco(subject, tenantId, roles, permisos, null, null, null);

        assertThat(vigenciaRefresco)
                .as("la vigencia de refresco configurada debe estar dentro de norma (<= 7 dias)")
                .isLessThanOrEqualTo(MAX_REFRESCO);

        assertThat(emitido.expiracion())
                .as("la expiracion emitida debe ser ahora + vigenciaConfigurada")
                .isEqualTo(AHORA.plus(vigenciaRefresco));

        Duration ttl = Duration.between(AHORA, emitido.expiracion());
        assertThat(ttl)
                .as("exp - ahora nunca excede 7 dias")
                .isLessThanOrEqualTo(MAX_REFRESCO)
                .as("exp - ahora es estrictamente positiva (exp > ahora)")
                .isPositive();

        ClaimsToken claims = servicio.validarTokenRefresco(emitido.valor());
        assertThat(claims.subject()).isEqualTo(subject);
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.roles()).containsExactlyElementsOf(roles);
        assertThat(claims.permisos()).containsExactlyElementsOf(permisos);
        assertThat(claims.esRefresco()).isTrue();
        assertThat(claims.expiracion()).isEqualTo(emitido.expiracion());
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    @Provide
    Arbitrary<String> subjects() {
        // Identificadores ASCII no vacios (p. ej. usuario-1, super-admin).
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(24)
                .withCharRange('a', 'z')
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-');
    }

    @Provide
    Arbitrary<UUID> tenants() {
        // tenant nulo (super_admin) o un UUID cualquiera.
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.create(UUID::randomUUID));
    }

    @Provide
    Arbitrary<List<String>> autoridades() {
        // Listas pequenas de nombres de rol/permiso ASCII (con ':' para permisos atomicos).
        Arbitrary<String> autoridad = Arbitraries.strings().ofMinLength(1).ofMaxLength(20)
                .withCharRange('a', 'z')
                .withChars(':', '_');
        return autoridad.list().ofMaxSize(5);
    }

    @Provide
    Arbitrary<Duration> vigenciasAcceso() {
        // Dentro de norma: PT1S..PT15M, incluyendo el maximo exacto como caso frontera.
        Arbitrary<Duration> enRango = Arbitraries.longs()
                .between(1L, MAX_ACCESO.getSeconds())
                .map(Duration::ofSeconds);
        return Arbitraries.oneOf(enRango, Arbitraries.just(MAX_ACCESO));
    }

    @Provide
    Arbitrary<Duration> vigenciasRefresco() {
        // Dentro de norma: PT1M..P7D, incluyendo el maximo exacto como caso frontera.
        Arbitrary<Duration> enRango = Arbitraries.longs()
                .between(60L, MAX_REFRESCO.getSeconds())
                .map(Duration::ofSeconds);
        return Arbitraries.oneOf(enRango, Arbitraries.just(MAX_REFRESCO));
    }
}
