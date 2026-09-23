package com.dessti.crm.platform.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
 * Pruebas unitarias de la regla <strong>la suspension de una Empresa impide el
 * inicio de sesion</strong> (Req 24.4) en {@link ServicioAutenticacion}. Usan
 * dobles de Mockito y un {@link Clock} fijo; no arrancan contexto de Spring.
 *
 * <p>Se verifica que: (1) con la Empresa con acceso bloqueado por su estado
 * (suspendida, Req 24.4; o cancelada en Periodo_Gracia, Req 69.2) el login se
 * rechaza con el mensaje <em>generico</em> (anti-enumeracion, Req 1.3) sin
 * comparar la contrasena ni emitir tokens, registrando el motivo en la auditoria
 * interna (Req 24.6); (2) con la Empresa activa el login procede con normalidad;
 * y (3) el super_admin (tenant_id NULL) no se ve afectado por la comprobacion.</p>
 */
class ServicioAutenticacionSuspensionTest {

    private static final Instant T0 = Instant.parse("2025-01-01T10:00:00Z");
    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String IDENTIFICADOR = "juan@empresa.com";
    private static final String HASH = "$2a$hash";
    private static final String IP = "203.0.113.7";

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
        // El tenant con Empresa pertenece a un Giro (Req 9.1); el super_admin
        // (tenant nulo) no consulta el Giro, por lo que este stub no aplica alli.
        when(giroEmpresa.giroDeTenant(TENANT)).thenReturn(Optional.of("anuncios-luminosos"));
        // El tenant con Empresa tiene modulos habilitados (Req 25.4); el
        // super_admin (tenant nulo) no consulta los modulos.
        when(modulosHabilitados.modulosHabilitadosDe(TENANT)).thenReturn(List.of("comercial"));
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        servicio = new ServicioAutenticacion(
                usuarioRepository, passwordEncoder, servicioTokens, auditoria,
                registroSesiones, estadoEmpresa, giroEmpresa, modulosHabilitados, clock);
    }

    @Test
    @DisplayName("Empresa suspendida: login rechazado con mensaje generico, sin comparar contrasena ni emitir tokens (Req 24.4, 1.3)")
    void empresaSuspendidaRechazaLogin() {
        UsuarioAuth usuario = UsuarioAuthTestFactory.activo(ID, TENANT, IDENTIFICADOR, HASH);
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(Optional.of(usuario));
        when(estadoEmpresa.accesoBloqueado(TENANT)).thenReturn(true);

        assertThatThrownBy(() -> servicio.login(IDENTIFICADOR, "buena", IP))
                .isInstanceOf(AutenticacionException.class)
                .hasMessage("Credenciales invalidas"); // generico (Req 1.3), no revela el estado

        // No se compara la contrasena ni se emiten tokens de una Empresa bloqueada.
        verify(passwordEncoder, never()).matches(any(), any());
        verify(servicioTokens, never()).emitirTokenAcceso(any(), any(), anyList(), anyList(), any(), any(), any());

        // El motivo se registra en la auditoria interna (Req 24.6).
        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        assertThat(captor.getValue().detalle()).contains("acceso bloqueado por estado de la empresa");
    }

    @Test
    @DisplayName("Empresa activa: el login procede y emite tokens (Req 24.4 no aplica)")
    void empresaActivaPermiteLogin() {
        UsuarioAuth usuario = UsuarioAuthTestFactory.activo(ID, TENANT, IDENTIFICADOR, HASH);
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(Optional.of(usuario));
        when(estadoEmpresa.accesoBloqueado(TENANT)).thenReturn(false);
        when(passwordEncoder.matches("buena", HASH)).thenReturn(true);
        when(usuarioRepository.buscarNombresRoles(ID)).thenReturn(List.of("ventas"));
        when(usuarioRepository.buscarPermisos(ID)).thenReturn(List.of("cliente:crear"));
        when(servicioTokens.emitirTokenAcceso(eq(ID.toString()), eq(TENANT), anyList(), anyList(), any(), any(), any()))
                .thenReturn(new TokenEmitido("acceso-jwt", T0.plusSeconds(900)));
        when(servicioTokens.emitirTokenRefresco(eq(ID.toString()), eq(TENANT), anyList(), anyList(), any(), any(), any()))
                .thenReturn(new TokenEmitido("refresco-jwt", T0.plusSeconds(604800)));

        TokenResponse respuesta = servicio.login(IDENTIFICADOR, "buena", IP);

        assertThat(respuesta.accessToken()).isEqualTo("acceso-jwt");
        assertThat(respuesta.refreshToken()).isEqualTo("refresco-jwt");
    }

    @Test
    @DisplayName("super_admin (tenant_id NULL): no se consulta la suspension y el login procede (Req 24.3)")
    void superAdminNoConsultaSuspension() {
        UsuarioAuth superAdmin = UsuarioAuthTestFactory.activo(ID, null, "root@plataforma", HASH);
        when(usuarioRepository.findByIdentificadorAcceso("root@plataforma"))
                .thenReturn(Optional.of(superAdmin));
        when(passwordEncoder.matches("buena", HASH)).thenReturn(true);
        when(usuarioRepository.buscarNombresRoles(ID)).thenReturn(List.of("super_admin"));
        when(usuarioRepository.buscarPermisos(ID)).thenReturn(List.of("empresa:listar"));
        // El super_admin (tenant nulo) no lleva Giro: el claim se emite con giro null.
        when(servicioTokens.emitirTokenAcceso(eq(ID.toString()), any(), anyList(), anyList(), any(), any(), any()))
                .thenReturn(new TokenEmitido("acceso-jwt", T0.plusSeconds(900)));
        when(servicioTokens.emitirTokenRefresco(eq(ID.toString()), any(), anyList(), anyList(), any(), any(), any()))
                .thenReturn(new TokenEmitido("refresco-jwt", T0.plusSeconds(604800)));

        TokenResponse respuesta = servicio.login("root@plataforma", "buena", IP);
        // El Giro nunca se consulta para un usuario de plataforma (tenant nulo).
        verify(giroEmpresa, never()).giroDeTenant(any());

        assertThat(respuesta.accessToken()).isEqualTo("acceso-jwt");
        // Nunca se consulta el bloqueo por estado para un usuario de plataforma (tenant NULL).
        verify(estadoEmpresa, never()).accesoBloqueado(any());
    }

    @Test
    @DisplayName("Empresa cancelada (Periodo_Gracia): acceso bloqueado, login rechazado con mensaje generico (Req 69.2)")
    void empresaCanceladaRechazaLogin() {
        UsuarioAuth usuario = UsuarioAuthTestFactory.activo(ID, TENANT, IDENTIFICADOR, HASH);
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(Optional.of(usuario));
        // accesoBloqueado devuelve true tanto para SUSPENDIDA como para CANCELADA.
        when(estadoEmpresa.accesoBloqueado(TENANT)).thenReturn(true);

        assertThatThrownBy(() -> servicio.login(IDENTIFICADOR, "buena", IP))
                .isInstanceOf(AutenticacionException.class)
                .hasMessage("Credenciales invalidas");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(servicioTokens, never()).emitirTokenAcceso(any(), any(), anyList(), anyList(), any(), any(), any());
    }
}
