package com.dessti.crm.platform.security.sesiones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del adaptador {@link RegistroSesionesJpaAdapter} (Req 68):
 * comportamiento de la denylist por {@code jti} (incluido el rechazo
 * conservador de un jti desconocido), la revocacion individual/en bloque y la
 * idempotencia del registro. El repositorio se sustituye por un doble de
 * Mockito y el reloj es fijo.
 */
class RegistroSesionesJpaAdapterTest {

    private static final Instant T0 = Instant.parse("2025-01-01T10:00:00Z");
    private static final UUID USUARIO = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private SesionRefrescoRepository repositorio;
    private Clock clock;
    private RegistroSesionesJpaAdapter adaptador;

    @BeforeEach
    void setUp() {
        repositorio = mock(SesionRefrescoRepository.class);
        clock = Clock.fixed(T0, ZoneOffset.UTC);
        adaptador = new RegistroSesionesJpaAdapter(repositorio, clock);
    }

    private SesionRefresco sesionActiva(String jti) {
        return new SesionRefresco(new RegistroSesion(
                jti, USUARIO, TENANT, T0, T0.plusSeconds(604800)));
    }

    @Test
    @DisplayName("registrar es idempotente por jti: no duplica si ya existe")
    void registrarEsIdempotente() {
        when(repositorio.existsByJti("jti-1")).thenReturn(true);

        adaptador.registrar(new RegistroSesion("jti-1", USUARIO, TENANT, T0, T0.plusSeconds(100)));

        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("estaRevocado: un jti desconocido se trata como revocado (rechazo conservador, Req 68)")
    void jtiDesconocidoSeTrataComoRevocado() {
        when(repositorio.findByJti("fantasma")).thenReturn(Optional.empty());
        assertThat(adaptador.estaRevocado("fantasma")).isTrue();
    }

    @Test
    @DisplayName("estaRevocado: una sesion activa NO esta revocada; una revocada SI")
    void estaRevocadoReflejaLaBandera() {
        SesionRefresco activa = sesionActiva("jti-activa");
        when(repositorio.findByJti("jti-activa")).thenReturn(Optional.of(activa));
        assertThat(adaptador.estaRevocado("jti-activa")).isFalse();

        SesionRefresco revocada = sesionActiva("jti-revocada");
        revocada.revocar(MotivoRevocacion.LOGOUT, clock);
        when(repositorio.findByJti("jti-revocada")).thenReturn(Optional.of(revocada));
        assertThat(adaptador.estaRevocado("jti-revocada")).isTrue();
    }

    @Test
    @DisplayName("estaRevocado: un jti nulo/vacio se rechaza de forma conservadora")
    void jtiVacioSeRechaza() {
        assertThat(adaptador.estaRevocado(null)).isTrue();
        assertThat(adaptador.estaRevocado("  ")).isTrue();
    }

    @Test
    @DisplayName("revocar marca la sesion, persiste y devuelve 1; una segunda vez devuelve 0 (idempotente)")
    void revocarEsIdempotente() {
        SesionRefresco sesion = sesionActiva("jti-x");
        when(repositorio.findByJti("jti-x")).thenReturn(Optional.of(sesion));

        assertThat(adaptador.revocar("jti-x", MotivoRevocacion.LOGOUT)).isEqualTo(1);
        assertThat(sesion.isRevocado()).isTrue();
        assertThat(sesion.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.LOGOUT);
        verify(repositorio).save(sesion);

        // Ya revocada: la segunda invocacion no cambia nada.
        assertThat(adaptador.revocar("jti-x", MotivoRevocacion.ADMINISTRADOR)).isZero();
    }

    @Test
    @DisplayName("revocar un jti inexistente devuelve 0 sin persistir")
    void revocarInexistenteDevuelveCero() {
        when(repositorio.findByJti("no-existe")).thenReturn(Optional.empty());
        assertThat(adaptador.revocar("no-existe", MotivoRevocacion.LOGOUT)).isZero();
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("revocarTodasDeUsuario delega el UPDATE en bloque con el instante del reloj")
    void revocarTodasDelegaEnRepositorio() {
        when(repositorio.revocarActivasDeUsuario(eq(USUARIO), eq(MotivoRevocacion.ADMINISTRADOR), eq(T0)))
                .thenReturn(3);

        int revocadas = adaptador.revocarTodasDeUsuario(USUARIO, MotivoRevocacion.ADMINISTRADOR);

        assertThat(revocadas).isEqualTo(3);
        verify(repositorio).revocarActivasDeUsuario(USUARIO, MotivoRevocacion.ADMINISTRADOR, T0);
    }
}
