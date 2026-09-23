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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.empresas.EstadoEmpresaPort;
import com.dessti.crm.platform.security.auth.rest.TokenResponse;
import com.dessti.crm.platform.security.jwt.ClaimsToken;
import com.dessti.crm.platform.security.jwt.ServicioTokensJwt;
import com.dessti.crm.platform.security.jwt.TipoToken;
import com.dessti.crm.platform.security.jwt.TokenEmitido;
import com.dessti.crm.platform.security.jwt.TokenInvalidoException;
import com.dessti.crm.platform.security.rbac.GiroEmpresaPort;
import com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort;
import com.dessti.crm.platform.security.sesiones.MotivoRevocacion;
import com.dessti.crm.platform.security.sesiones.RegistroSesion;
import com.dessti.crm.platform.security.sesiones.RegistroSesionesPort;

/**
 * Pruebas unitarias del cableado de <strong>gestion y revocacion de Sesiones</strong>
 * en {@link ServicioAutenticacion} (Req 68, tarea 11.2): registro de la Sesion
 * en login, rechazo del refresco revocado, rotacion del refresco y revocacion en
 * logout. Usa dobles de Mockito y un {@link Clock} fijo; no arranca Spring.
 */
class ServicioAutenticacionSesionesTest {

    private static final Instant T0 = Instant.parse("2025-01-01T10:00:00Z");
    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String IDENTIFICADOR = "juan@empresa.com";
    private static final String HASH = "$2a$hash";
    private static final String IP = "203.0.113.7";
    /** Clave del Giro de la Empresa del Usuario, expuesta en el claim (Req 9.1). */
    private static final String GIRO = "anuncios-luminosos";
    /** Modulos habilitados de la Empresa, expuestos en el claim (Req 25.4). */
    private static final List<String> MODULOS = List.of("comercial", "facturacion");

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
        // Por defecto, la Empresa NO tiene el acceso bloqueado por su estado (Req 24.4/69.2).
        when(estadoEmpresa.accesoBloqueado(any())).thenReturn(false);
        // Por defecto, el tenant pertenece al Giro 'anuncios-luminosos' (Req 9.1).
        when(giroEmpresa.giroDeTenant(any())).thenReturn(java.util.Optional.of(GIRO));
        // Por defecto, la Empresa tiene un conjunto de modulos habilitados (Req 25.4).
        when(modulosHabilitados.modulosHabilitadosDe(any())).thenReturn(MODULOS);
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        servicio = new ServicioAutenticacion(
                usuarioRepository, passwordEncoder, servicioTokens, auditoria,
                registroSesiones, estadoEmpresa, giroEmpresa, modulosHabilitados, clock);
    }

    private ClaimsToken claimsRefresco(String jti) {
        return new ClaimsToken(ID.toString(), TENANT, List.of("ventas"),
                List.of("cliente:crear"), TipoToken.REFRESCO, T0.plusSeconds(604800), jti, GIRO,
                IDENTIFICADOR, MODULOS);
    }

    // -----------------------------------------------------------------
    // login: registra la Sesion (Req 68.3)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Login exitoso registra la Sesion del Token_Refresco con su jti (Req 68.3)")
    void loginRegistraLaSesion() {
        UsuarioAuth usuario = UsuarioAuthTestFactory.activo(ID, TENANT, IDENTIFICADOR, HASH);
        when(usuarioRepository.findByIdentificadorAcceso(IDENTIFICADOR)).thenReturn(java.util.Optional.of(usuario));
        when(passwordEncoder.matches("buena", HASH)).thenReturn(true);
        when(usuarioRepository.buscarNombresRoles(ID)).thenReturn(List.of("ventas"));
        when(usuarioRepository.buscarPermisos(ID)).thenReturn(List.of("cliente:crear"));
        when(servicioTokens.emitirTokenAcceso(eq(ID.toString()), eq(TENANT), anyList(), anyList(), eq(GIRO), eq(IDENTIFICADOR), anyList()))
                .thenReturn(new TokenEmitido("acceso-jwt", T0.plusSeconds(900), "jti-acceso"));
        when(servicioTokens.emitirTokenRefresco(eq(ID.toString()), eq(TENANT), anyList(), anyList(), eq(GIRO), eq(IDENTIFICADOR), anyList()))
                .thenReturn(new TokenEmitido("refresco-jwt", T0.plusSeconds(604800), "jti-refresco"));

        TokenResponse respuesta = servicio.login(IDENTIFICADOR, "buena", IP);

        assertThat(respuesta.refreshToken()).isEqualTo("refresco-jwt");
        ArgumentCaptor<RegistroSesion> captor = ArgumentCaptor.forClass(RegistroSesion.class);
        verify(registroSesiones).registrar(captor.capture());
        RegistroSesion registrada = captor.getValue();
        assertThat(registrada.jti()).isEqualTo("jti-refresco");
        assertThat(registrada.usuarioId()).isEqualTo(ID);
        assertThat(registrada.tenantId()).isEqualTo(TENANT);
        assertThat(registrada.expiraEn()).isEqualTo(T0.plusSeconds(604800));
    }

    // -----------------------------------------------------------------
    // refresh: rechazo del refresco revocado (Req 1.9, 68.3)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Refresh con jti revocado se rechaza con 401 y no emite tokens (Req 1.9, 68.3)")
    void refreshConJtiRevocadoSeRechaza() {
        when(servicioTokens.validarTokenRefresco("refresco-x")).thenReturn(claimsRefresco("jti-viejo"));
        when(registroSesiones.estaRevocado("jti-viejo")).thenReturn(true);

        assertThatThrownBy(() -> servicio.refresh("refresco-x"))
                .isInstanceOf(AutenticacionException.class);

        verify(servicioTokens, never()).emitirTokenAcceso(any(), any(), anyList(), anyList(), any(), any(), any());
        verify(registroSesiones, never()).revocar(any(), any());
    }

    @Test
    @DisplayName("Refresh con Token_Refresco invalido se rechaza con 401 (Req 1.9)")
    void refreshConTokenInvalidoSeRechaza() {
        when(servicioTokens.validarTokenRefresco("basura"))
                .thenThrow(new TokenInvalidoException("invalido"));

        assertThatThrownBy(() -> servicio.refresh("basura"))
                .isInstanceOf(AutenticacionException.class);

        verify(registroSesiones, never()).estaRevocado(any());
        verify(servicioTokens, never()).emitirTokenAcceso(any(), any(), anyList(), anyList(), any(), any(), any());
    }

    // -----------------------------------------------------------------
    // refresh: rotacion del Token_Refresco (Req 68)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Refresh valido rota el refresco: revoca el jti viejo y registra el nuevo (Req 68)")
    void refreshRotaElTokenRefresco() {
        when(servicioTokens.validarTokenRefresco("refresco-vigente")).thenReturn(claimsRefresco("jti-viejo"));
        when(registroSesiones.estaRevocado("jti-viejo")).thenReturn(false);
        when(servicioTokens.emitirTokenAcceso(eq(ID.toString()), eq(TENANT), anyList(), anyList(), eq(GIRO), eq(IDENTIFICADOR), anyList()))
                .thenReturn(new TokenEmitido("nuevo-acceso", T0.plusSeconds(900), "jti-acceso-2"));
        when(servicioTokens.emitirTokenRefresco(eq(ID.toString()), eq(TENANT), anyList(), anyList(), eq(GIRO), eq(IDENTIFICADOR), anyList()))
                .thenReturn(new TokenEmitido("nuevo-refresco", T0.plusSeconds(604800), "jti-nuevo"));

        TokenResponse respuesta = servicio.refresh("refresco-vigente");

        // El jti viejo queda revocado (rotacion) y el refresco devuelto es el nuevo.
        verify(registroSesiones).revocar(eq("jti-viejo"), any(MotivoRevocacion.class));
        assertThat(respuesta.refreshToken()).isEqualTo("nuevo-refresco");
        assertThat(respuesta.accessToken()).isEqualTo("nuevo-acceso");

        ArgumentCaptor<RegistroSesion> captor = ArgumentCaptor.forClass(RegistroSesion.class);
        verify(registroSesiones).registrar(captor.capture());
        assertThat(captor.getValue().jti()).isEqualTo("jti-nuevo");
    }

    // -----------------------------------------------------------------
    // logout: revoca el jti del Token_Refresco (Req 68.1)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Logout revoca el jti del Token_Refresco presentado (Req 68.1)")
    void logoutRevocaElRefresco() {
        when(servicioTokens.validarTokenRefresco("refresco-a-cerrar")).thenReturn(claimsRefresco("jti-a"));
        when(registroSesiones.revocar("jti-a", MotivoRevocacion.LOGOUT)).thenReturn(1);

        servicio.logout("refresco-a-cerrar");

        verify(registroSesiones).revocar("jti-a", MotivoRevocacion.LOGOUT);
        // Se audita la revocacion efectiva del logout.
        verify(auditoria).registrar(any());
    }

    @Test
    @DisplayName("Logout con Token_Refresco invalido no falla ni revoca (idempotente)")
    void logoutConRefrescoInvalidoNoFalla() {
        when(servicioTokens.validarTokenRefresco("basura"))
                .thenThrow(new TokenInvalidoException("invalido"));

        servicio.logout("basura");

        verify(registroSesiones, never()).revocar(any(), any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("Logout con cuerpo vacio es no-op (no revoca, no audita)")
    void logoutVacioEsNoOp() {
        servicio.logout("   ");

        verify(servicioTokens, never()).validarTokenRefresco(any());
        verify(registroSesiones, never()).revocar(any(), any());
    }
}
