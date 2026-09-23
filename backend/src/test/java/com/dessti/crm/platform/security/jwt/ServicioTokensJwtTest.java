package com.dessti.crm.platform.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del servicio de tokens JWT (tarea 9.1, Req 1).
 *
 * <p>Verifican emision con claims correctos, limites de vigencia
 * (acceso &le; 15 min, refresco &le; 7 dias), validacion de token
 * valido/expirado/firma invalida, separacion por tipo de token y extraccion de
 * {@code tenant_id}/roles/permisos. Se usa una clave de PRUEBA (no real) y un
 * reloj fijo para determinismo.</p>
 */
class ServicioTokensJwtTest {

    // Clave de PRUEBA de >= 32 bytes (256 bits) para HS256. No es un secreto real.
    private static final String CLAVE_PRUEBA = "clave-de-prueba-para-jwt-hs256-32bytes-min!!";

    private static final Instant AHORA = Instant.parse("2025-01-15T10:00:00Z");
    private final Clock relojFijo = Clock.fixed(AHORA, ZoneOffset.UTC);

    private ServicioTokensJwt servicioCon(Duration vigenciaAcceso, Duration vigenciaRefresco) {
        JwtProperties props = new JwtProperties("crm-test", vigenciaAcceso, vigenciaRefresco);
        return new ServicioTokensJwt(CLAVE_PRUEBA, props, relojFijo);
    }

    private ServicioTokensJwt servicioPorDefecto() {
        return servicioCon(Duration.ofMinutes(15), Duration.ofDays(7));
    }

    @Test
    void emiteTokenAccesoConClaimsCorrectos() {
        ServicioTokensJwt servicio = servicioPorDefecto();
        UUID tenant = UUID.randomUUID();

        TokenEmitido emitido = servicio.emitirTokenAcceso(
                "usuario-1", tenant, List.of("ventas"), List.of("cliente:crear"),
                "anuncios-luminosos", "superadmin@dessti", List.of("comercial", "facturacion"));

        ClaimsToken claims = servicio.validarTokenAcceso(emitido.valor());
        assertThat(claims.subject()).isEqualTo("usuario-1");
        assertThat(claims.tenantId()).isEqualTo(tenant);
        assertThat(claims.roles()).containsExactly("ventas");
        assertThat(claims.permisos()).containsExactly("cliente:crear");
        assertThat(claims.giro()).isEqualTo("anuncios-luminosos");
        assertThat(claims.identificador()).isEqualTo("superadmin@dessti");
        assertThat(claims.modulos()).containsExactly("comercial", "facturacion");
        assertThat(claims.esAcceso()).isTrue();
        assertThat(claims.expiracion()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
    }

    @Test
    void emiteTokenAccesoSinTenantParaSuperAdmin() {
        ServicioTokensJwt servicio = servicioPorDefecto();

        TokenEmitido emitido = servicio.emitirTokenAcceso(
                "super-admin", null, List.of("super_admin"), List.of(), null, "superadmin@dessti", null);

        ClaimsToken claims = servicio.validarTokenAcceso(emitido.valor());
        assertThat(claims.tenantId()).isNull();
        assertThat(claims.roles()).containsExactly("super_admin");
        // El super_admin (tenant nulo) no pertenece a ninguna Empresa: sin Giro (Req 9.1).
        assertThat(claims.giro()).isNull();
        // El identificador legible SI viaja aunque el tenant sea nulo (super_admin).
        assertThat(claims.identificador()).isEqualTo("superadmin@dessti");
        // El super_admin no se rige por el gating de modulos: claim omitido, que
        // ClaimsToken normaliza a lista vacia al leer (Req 25.4).
        assertThat(claims.modulos()).isEmpty();
    }

    @Test
    void emiteYLeeElClaimModulos() {
        // Los modulos habilitados de la Empresa viajan en AMBOS tokens y se
        // recuperan al validar (Req 25.4).
        ServicioTokensJwt servicio = servicioPorDefecto();
        UUID tenant = UUID.randomUUID();

        TokenEmitido acceso = servicio.emitirTokenAcceso(
                "u-mod", tenant, List.of("ventas"), List.of("cliente:crear"),
                "manufactura", "u-mod", List.of("comercial", "operacion"));
        TokenEmitido refresco = servicio.emitirTokenRefresco(
                "u-mod", tenant, List.of("ventas"), List.of("cliente:crear"),
                "manufactura", "u-mod", List.of("comercial", "operacion"));

        assertThat(servicio.validarTokenAcceso(acceso.valor()).modulos())
                .containsExactly("comercial", "operacion");
        // Los modulos tambien viajan en el Token_Refresco para reemitir sin BD.
        assertThat(servicio.validarTokenRefresco(refresco.valor()).modulos())
                .containsExactly("comercial", "operacion");
    }

    @Test
    void moduloVacioEmiteClaimVacio() {
        // Una Empresa con override vacio (cero modulos) lleva el claim presente
        // pero vacio ([]), distinto de "omitido" del super_admin (Req 25.4).
        ServicioTokensJwt servicio = servicioPorDefecto();
        UUID tenant = UUID.randomUUID();

        TokenEmitido emitido = servicio.emitirTokenAcceso(
                "u-vacio", tenant, List.of(), List.of(), "manufactura", "u-vacio", List.of());

        assertThat(servicio.validarTokenAcceso(emitido.valor()).modulos()).isEmpty();
    }

    @Test
    void emiteYLeeElClaimGiro() {
        // El Giro de la Empresa viaja en el JWT y se recupera al validar (Req 9.1).
        ServicioTokensJwt servicio = servicioPorDefecto();
        UUID tenant = UUID.randomUUID();

        TokenEmitido acceso = servicio.emitirTokenAcceso(
                "u-giro", tenant, List.of("ventas"), List.of("cliente:crear"), "manufactura", "u-giro", List.of());
        TokenEmitido refresco = servicio.emitirTokenRefresco(
                "u-giro", tenant, List.of("ventas"), List.of("cliente:crear"), "manufactura", "u-giro", List.of());

        assertThat(servicio.validarTokenAcceso(acceso.valor()).giro()).isEqualTo("manufactura");
        // El Giro tambien viaja en el Token_Refresco para reemitir sin BD (Req 1.5, 9.1).
        assertThat(servicio.validarTokenRefresco(refresco.valor()).giro()).isEqualTo("manufactura");
    }

    @Test
    void giroEnBlancoNoEmiteElClaim() {
        // Un giro nulo o en blanco omite el claim, coherente con tenant_id nulo (Req 9.1).
        ServicioTokensJwt servicio = servicioPorDefecto();
        UUID tenant = UUID.randomUUID();

        TokenEmitido conNulo = servicio.emitirTokenAcceso(
                "u", tenant, List.of(), List.of(), null, null, null);
        TokenEmitido conBlanco = servicio.emitirTokenAcceso(
                "u", tenant, List.of(), List.of(), "   ", null, null);

        assertThat(servicio.validarTokenAcceso(conNulo.valor()).giro()).isNull();
        assertThat(servicio.validarTokenAcceso(conBlanco.valor()).giro()).isNull();
    }

    @Test
    void vigenciaAccesoNoExcede15Minutos() {
        ServicioTokensJwt servicio = servicioPorDefecto();
        TokenEmitido emitido = servicio.emitirTokenAcceso("u", null, List.of(), List.of(), null, null, null);
        Duration vigencia = Duration.between(AHORA, emitido.expiracion());
        assertThat(vigencia).isLessThanOrEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void vigenciaRefrescoNoExcede7Dias() {
        ServicioTokensJwt servicio = servicioPorDefecto();
        TokenEmitido emitido = servicio.emitirTokenRefresco("u", null, List.of(), List.of(), null, null, null);
        Duration vigencia = Duration.between(AHORA, emitido.expiracion());
        assertThat(vigencia).isLessThanOrEqualTo(Duration.ofDays(7));
    }

    @Test
    void rechazaConfiguracionConVigenciaAccesoFueraDeNorma() {
        assertThatThrownBy(() -> servicioCon(Duration.ofMinutes(16), Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaConfiguracionConVigenciaRefrescoFueraDeNorma() {
        assertThatThrownBy(() -> servicioCon(Duration.ofMinutes(15), Duration.ofDays(8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaClaveDebil() {
        JwtProperties props = new JwtProperties("crm-test",
                Duration.ofMinutes(15), Duration.ofDays(7));
        assertThatThrownBy(() -> new ServicioTokensJwt("corta", props, relojFijo))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaTokenExpirado() {
        // Emite con el reloj fijo y valida con un reloj adelantado 16 minutos.
        ServicioTokensJwt emisor = servicioPorDefecto();
        TokenEmitido emitido = emisor.emitirTokenAcceso("u", null, List.of(), List.of(), null, null, null);

        Clock relojFuturo = Clock.fixed(AHORA.plus(Duration.ofMinutes(16)), ZoneOffset.UTC);
        ServicioTokensJwt validador = new ServicioTokensJwt(
                CLAVE_PRUEBA, new JwtProperties("crm-test", Duration.ofMinutes(15), Duration.ofDays(7)),
                relojFuturo);

        assertThatThrownBy(() -> validador.validarTokenAcceso(emitido.valor()))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaTokenConFirmaInvalida() {
        ServicioTokensJwt emisor = servicioPorDefecto();
        TokenEmitido emitido = emisor.emitirTokenAcceso("u", null, List.of(), List.of(), null, null, null);

        // Otro servicio con una clave distinta no debe validar la firma.
        ServicioTokensJwt otro = new ServicioTokensJwt(
                "otra-clave-de-prueba-distinta-de-32-bytes-minimo!!",
                new JwtProperties("crm-test", Duration.ofMinutes(15), Duration.ofDays(7)),
                relojFijo);

        assertThatThrownBy(() -> otro.validarTokenAcceso(emitido.valor()))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaTokenMalformado() {
        ServicioTokensJwt servicio = servicioPorDefecto();
        assertThatThrownBy(() -> servicio.validarTokenAcceso("esto-no-es-un-jwt"))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void noAceptaTokenRefrescoComoAcceso() {
        ServicioTokensJwt servicio = servicioPorDefecto();
        TokenEmitido refresco = servicio.emitirTokenRefresco("u", null, List.of(), List.of(), null, null, null);
        assertThatThrownBy(() -> servicio.validarTokenAcceso(refresco.valor()))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void noAceptaTokenAccesoComoRefresco() {
        ServicioTokensJwt servicio = servicioPorDefecto();
        TokenEmitido acceso = servicio.emitirTokenAcceso("u", null, List.of(), List.of(), null, null, null);
        assertThatThrownBy(() -> servicio.validarTokenRefresco(acceso.valor()))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void validaTokenRefrescoYExtraeClaims() {
        ServicioTokensJwt servicio = servicioPorDefecto();
        UUID tenant = UUID.randomUUID();
        TokenEmitido refresco = servicio.emitirTokenRefresco(
                "u-9", tenant, List.of("marketing"), List.of("conversacion:leer"),
                "anuncios-luminosos", "vendedor@empresa", List.of("comercial"));

        ClaimsToken claims = servicio.validarTokenRefresco(refresco.valor());
        assertThat(claims.esRefresco()).isTrue();
        assertThat(claims.subject()).isEqualTo("u-9");
        assertThat(claims.tenantId()).isEqualTo(tenant);
        assertThat(claims.roles()).containsExactly("marketing");
        assertThat(claims.permisos()).containsExactly("conversacion:leer");
        assertThat(claims.giro()).isEqualTo("anuncios-luminosos");
        // El identificador legible tambien viaja en el Token_Refresco para
        // preservarlo al reemitir sin BD (misma via que giro).
        assertThat(claims.identificador()).isEqualTo("vendedor@empresa");
    }
}
