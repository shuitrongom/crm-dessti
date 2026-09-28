package com.dessti.crm.platform.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del claim {@code cliente_id} que habilita el Portal del Cliente (Req 45):
 * al emitir un token con un Cliente asociado, la validacion lo recupera intacto; y
 * al emitir sin Cliente (staff/super_admin), el claim se omite y la validacion
 * devuelve {@code null}. Reutiliza {@link ServicioTokensJwt} con clave de PRUEBA y
 * reloj fijo (pieza pura, sin BD ni Spring).
 */
class ClienteIdClaimTest {

    private static final String CLAVE_PRUEBA = "clave-de-firma-jwt-solo-para-pruebas-0123456789";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC);

    private ServicioTokensJwt servicio() {
        JwtProperties props = new JwtProperties(
                "crm-test", Duration.ofMinutes(15), Duration.ofDays(7));
        return new ServicioTokensJwt(CLAVE_PRUEBA, props, RELOJ);
    }

    @Test
    @DisplayName("Emitir con clienteId lo recupera intacto al validar el token de acceso")
    void clienteIdRoundTripAcceso() {
        ServicioTokensJwt servicio = servicio();
        UUID cliente = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();

        TokenEmitido token = servicio.emitirTokenAcceso(
                UUID.randomUUID().toString(), tenant, List.of("cliente_portal"), List.of(),
                "anuncios-luminosos", "cliente@empresa", List.of("comercial"), cliente);

        ClaimsToken claims = servicio.validarTokenAcceso(token.valor());
        assertThat(claims.clienteId()).isEqualTo(cliente);
    }

    @Test
    @DisplayName("Emitir sin clienteId omite el claim; al validar devuelve null (staff/super_admin)")
    void sinClienteIdEsNull() {
        ServicioTokensJwt servicio = servicio();
        UUID tenant = UUID.randomUUID();

        // Sobrecarga de 7 args (sin clienteId): debe seguir funcionando sin claim.
        TokenEmitido token = servicio.emitirTokenAcceso(
                UUID.randomUUID().toString(), tenant, List.of("ventas"), List.of("cliente:crear"),
                "anuncios-luminosos", "staff@empresa", List.of("comercial"));

        ClaimsToken claims = servicio.validarTokenAcceso(token.valor());
        assertThat(claims.clienteId()).isNull();
    }

    @Test
    @DisplayName("El clienteId tambien viaja y se recupera en el token de refresco")
    void clienteIdRoundTripRefresco() {
        ServicioTokensJwt servicio = servicio();
        UUID cliente = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();

        TokenEmitido token = servicio.emitirTokenRefresco(
                UUID.randomUUID().toString(), tenant, List.of("cliente_portal"), List.of(),
                "anuncios-luminosos", "cliente@empresa", List.of("comercial"), cliente);

        ClaimsToken claims = servicio.validarTokenRefresco(token.valor());
        assertThat(claims.clienteId()).isEqualTo(cliente);
    }
}
