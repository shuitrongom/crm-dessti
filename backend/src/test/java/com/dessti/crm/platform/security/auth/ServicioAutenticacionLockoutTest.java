package com.dessti.crm.platform.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.empresas.EstadoEmpresaPort;
import com.dessti.crm.platform.security.auth.rest.TokenResponse;
import com.dessti.crm.platform.security.jwt.ServicioTokensJwt;
import com.dessti.crm.platform.security.jwt.TokenEmitido;
import com.dessti.crm.platform.security.rbac.GiroEmpresaPort;
import com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort;
import com.dessti.crm.platform.security.sesiones.RegistroSesionesPort;

/**
 * Pruebas unitarias del cableado del bloqueo por intentos fallidos y de la
 * auditoria en {@link ServicioAutenticacion} (Req 2.1, 2.2, 2.3, 2.5). Usa
 * dobles de Mockito y un {@link Clock} fijo; no arranca contexto de Spring.
 */
class ServicioAutenticacionLockoutTest {

    private static final Instant T0 = Instant.parse("2025-01-01T10:00:00Z");
    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String IDENTIFICADOR = "juan@empresa.com";
    private static final String HASH = "$2a$hash";
    private static final String IP = "203.0.113.7";
    /** Clave del Giro de la Empresa del Usuario, expuesta en el claim (Req 9.1). */
    private static final String GIRO = "anuncios-luminosos";

    private UsuarioAuthRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private ServicioTokensJwt servicioTokens;
    private AuditoriaPort auditoria;
    private RegistroSesionesPort registroSesiones;
    private EstadoEmpresaPort estadoEmpresa;
    private GiroEmpresaPort giroEmpresa;
    private ModulosHabilitadosPort modulosHabilitados;
    private ServicioAutenticacion servicio;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioAuthRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        servicioTokens = mock(ServicioTokensJwt.class);
        auditoria = mock(AuditoriaPort.class);
        registroSesiones = mock(RegistroSesionesPort.class);
        estadoEmpresa = mock(EstadoEmpresaPort.class);
        giroEmpresa = mock(GiroEmpresaPort.class);
        modulosHabilitados = mock(ModulosHabilitadosPort.class);
        // Por defecto, la Empresa NO tiene el acceso bloqueado por su estado
        // (Req 24.4/69.2): estos casos se centran en el bloqueo por intentos
        // fallidos, no en el estado de la Empresa.
        when(estadoEmpresa.accesoBloqueado(any())).thenReturn(false);
        // Por defecto, el tenant pertenece al Giro 'anuncios-luminosos' (Req 9.1).
        when(giroEmpresa.giroDeTenant(any())).thenReturn(Optional.of(GIRO));
        // Por defecto, la Empresa tiene modulos habilitados (Req 25.4).
        when(modulosHabilitados.modulosHabilitadosDe(any())).thenReturn(List.of("comercial"));
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        servicio = new ServicioAutenticacion(
                usuarioRepository, passwordEncoder, servicioTokens, auditoria,
                registroSesiones, estadoEmpresa, giroEmpresa, modulosHabilitados, clock);
    }

    private UsuarioAuth usuarioActivo() {
        return UsuarioAuthTestFactory.activo(ID, TENANT, IDENTIFICADOR, HASH);
    }

    @Test
    @DisplayName("Contrasena incorrecta incrementa intentos, persiste y audita el fallo")
    void falloIncrementaYAudita() {
        UsuarioAuth usuario = usuarioActivo();
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", HASH)).thenReturn(false);

        assertThatThrownBy(() -> servicio.login(IDENTIFICADOR, "mala", IP))
                .isInstanceOf(AutenticacionException.class);

        assertThat(usuario.getIntentosFallidos()).isEqualTo(1);
        verify(usuarioRepository).save(usuario);
        verify(auditoria).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("El 5to fallo consecutivo bloquea la cuenta y responde con el mensaje de bloqueo (Req 2.2)")
    void quintoFalloBloquea() {
        UsuarioAuth usuario = usuarioActivo();
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", HASH)).thenReturn(false);

        // Los primeros 4 fallos responden con el mensaje generico (Req 1.3).
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> servicio.login(IDENTIFICADOR, "mala", IP))
                    .isInstanceOf(AutenticacionException.class)
                    .hasMessage("Credenciales invalidas");
        }

        // El 5to fallo dispara el bloqueo: como la cuenta ya queda bloqueada, la
        // respuesta pasa a ser el mensaje de bloqueo con los minutos restantes
        // (Req 2.2), no el generico.
        assertThatThrownBy(() -> servicio.login(IDENTIFICADOR, "mala", IP))
                .isInstanceOf(AutenticacionException.class)
                .hasMessageContaining("temporalmente bloqueada")
                .hasMessageContaining("15 minutos");

        assertThat(usuario.getIntentosFallidos()).isEqualTo(5);
        assertThat(usuario.getBloqueadoHasta()).isNotNull();
    }

    @Test
    @DisplayName("Cuenta bloqueada: rechaza con mensaje de bloqueo + minutos restantes (Req 2.2) y audita el rechazo (Req 2.5)")
    void cuentaBloqueadaRechazaConMensajeYAudita() {
        UsuarioAuth usuario = usuarioActivo();
        // Se bloquea con 5 fallos previos en T0.
        Clock relojT0 = Clock.fixed(T0, ZoneOffset.UTC);
        for (int i = 0; i < 5; i++) {
            usuario.registrarFallo(relojT0);
        }
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(Optional.of(usuario));

        // Req 2.2: el mensaje indica el bloqueo temporal y el tiempo restante en
        // minutos. El servicio usa un Clock fijo en T0 (mismo instante del
        // bloqueo), asi que restan los 15 minutos completos.
        assertThatThrownBy(() -> servicio.login(IDENTIFICADOR, "loQueSea", IP))
                .isInstanceOf(AutenticacionException.class)
                .hasMessageContaining("temporalmente bloqueada")
                .hasMessageContaining("15 minutos");

        // No se compara la contrasena cuando la cuenta esta bloqueada.
        verify(passwordEncoder, never()).matches(any(), any());
        // Se registra el rechazo por bloqueo (Req 2.5).
        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        assertThat(captor.getValue().detalle()).contains("rechazado_por_bloqueo");
        // El evento nunca contiene la contrasena.
        assertThat(captor.getValue().detalle()).doesNotContain("loQueSea");
    }

    @Test
    @DisplayName("Contrasena incorrecta ANTES de bloquear: mensaje generico (Req 1.3), no revela el estado")
    void contrasenaIncorrectaAntesDeBloquearEsGenerica() {
        UsuarioAuth usuario = usuarioActivo();
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", HASH)).thenReturn(false);

        assertThatThrownBy(() -> servicio.login(IDENTIFICADOR, "mala", IP))
                .isInstanceOf(AutenticacionException.class)
                .hasMessage("Credenciales invalidas");

        assertThat(usuario.getIntentosFallidos()).isEqualTo(1);
        assertThat(usuario.estaBloqueado(Clock.fixed(T0, ZoneOffset.UTC))).isFalse();
    }

    @Test
    @DisplayName("Cuenta inactiva: mensaje generico (Req 1.3), no revela el estado de la cuenta")
    void cuentaInactivaEsGenerica() {
        UsuarioAuth usuario = UsuarioAuthTestFactory.inactivo(ID, TENANT, IDENTIFICADOR, HASH);
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servicio.login(IDENTIFICADOR, "loQueSea", IP))
                .isInstanceOf(AutenticacionException.class)
                .hasMessage("Credenciales invalidas");

        // No se compara la contrasena de una cuenta inactiva.
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Login exitoso reinicia el contador, persiste, audita y emite tokens")
    void exitoReiniciaYEmiteTokens() {
        UsuarioAuth usuario = usuarioActivo();
        usuario.registrarFallo(Clock.fixed(T0, ZoneOffset.UTC)); // 1 fallo previo
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("buena", HASH)).thenReturn(true);
        when(usuarioRepository.buscarNombresRoles(ID)).thenReturn(List.of("ventas"));
        when(usuarioRepository.buscarPermisos(ID)).thenReturn(List.of("cliente:crear"));
        when(servicioTokens.emitirTokenAcceso(eq(ID.toString()), eq(TENANT), anyList(), anyList(), eq(GIRO), eq(IDENTIFICADOR), anyList()))
                .thenReturn(new TokenEmitido("acceso-jwt", T0.plusSeconds(900)));
        when(servicioTokens.emitirTokenRefresco(eq(ID.toString()), eq(TENANT), anyList(), anyList(), eq(GIRO), eq(IDENTIFICADOR), anyList()))
                .thenReturn(new TokenEmitido("refresco-jwt", T0.plusSeconds(604800)));

        TokenResponse respuesta = servicio.login(IDENTIFICADOR, "buena", IP);

        assertThat(respuesta.accessToken()).isEqualTo("acceso-jwt");
        assertThat(respuesta.refreshToken()).isEqualTo("refresco-jwt");
        assertThat(usuario.getIntentosFallidos()).isZero();
        assertThat(usuario.getBloqueadoHasta()).isNull();
        verify(usuarioRepository).save(usuario);
        verify(auditoria).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("Identificador inexistente: error generico y auditoria de intento fallido, sin tocar el repositorio de escritura")
    void identificadorInexistente() {
        when(usuarioRepository.findByIdentificadorAcceso("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.login("nadie", "x", IP))
                .isInstanceOf(AutenticacionException.class)
                .hasMessage("Credenciales invalidas"); // generico (Req 1.3)

        verify(usuarioRepository, never()).save(any());
        verify(auditoria, times(1)).registrar(any(EventoAuditoria.class));
    }
}
