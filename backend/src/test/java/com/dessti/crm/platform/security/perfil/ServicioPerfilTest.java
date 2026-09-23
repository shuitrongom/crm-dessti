package com.dessti.crm.platform.security.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.UsuarioAutenticado;
import com.dessti.crm.platform.security.usuarios.Usuario;
import com.dessti.crm.platform.security.usuarios.UsuarioRepository;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas unitarias de {@link ServicioPerfil} (CHANGE 2). Usan dobles de Mockito
 * y fijan el principal en el {@code SecurityContextHolder}; no arrancan Spring ni
 * base de datos.
 *
 * <p>Verifican: consulta de perfil propio; cambio de contrasena con actual
 * correcta (cambia el hash y, para un Usuario de Empresa, fija el tenant antes
 * del save por RLS); rechazo con 422 cuando la actual es incorrecta; y que el
 * super_admin (tenant NULL) NO fija ningun tenant al cambiar su contrasena.</p>
 */
class ServicioPerfilTest {

    private static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private AuditoriaPort auditoria;
    private TenantSessionInitializer tenantSession;
    private ServicioPerfil servicio;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditoria = mock(AuditoriaPort.class);
        tenantSession = mock(TenantSessionInitializer.class);
        servicio = new ServicioPerfil(usuarioRepository, passwordEncoder, auditoria, tenantSession);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(UUID usuarioId, UUID tenantId) {
        UsuarioAutenticado principal = new UsuarioAutenticado(usuarioId.toString(), tenantId);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Usuario usuarioEmpresa(UUID tenantId, String ident, String hash) {
        return Usuario.crear(tenantId, ident, hash, null, Set.of(), "sistema");
    }

    @Test
    @DisplayName("consultarPerfil devuelve el identificador legible y el tenant del Usuario autenticado")
    void consultarPerfilDevuelveDatos() {
        Usuario usuario = usuarioEmpresa(TENANT, "admin@empresa.com", "$2a$hash");
        autenticarComo(usuario.getId(), TENANT);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        PerfilDto dto = servicio.consultarPerfil();

        assertThat(dto.id()).isEqualTo(usuario.getId());
        assertThat(dto.identificador()).isEqualTo("admin@empresa.com");
        assertThat(dto.tenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("cambiarPasswordPropia con actual correcta cambia el hash y fija el tenant antes del save (RLS)")
    void cambiarPasswordActualCorrectaEmpresa() {
        Usuario usuario = usuarioEmpresa(TENANT, "admin@empresa.com", "$2a$viejo");
        autenticarComo(usuario.getId(), TENANT);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Actual123", "$2a$viejo")).thenReturn(true);
        when(passwordEncoder.encode("NuevaSegura12345")).thenReturn("$2a$nuevo");

        servicio.cambiarPasswordPropia("Actual123", "NuevaSegura12345");

        assertThat(usuario.getHashPassword()).isEqualTo("$2a$nuevo");
        // Un Usuario de Empresa exige fijar el tenant ANTES del save (RLS V48/V53).
        InOrder orden = inOrder(tenantSession, usuarioRepository);
        orden.verify(tenantSession).applyTenant(TENANT);
        orden.verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    @DisplayName("cambiarPasswordPropia con actual incorrecta lanza ReglaNegocioException (422) y no cambia")
    void cambiarPasswordActualIncorrecta() {
        Usuario usuario = usuarioEmpresa(TENANT, "admin@empresa.com", "$2a$viejo");
        autenticarComo(usuario.getId(), TENANT);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Mala", "$2a$viejo")).thenReturn(false);

        assertThatThrownBy(() -> servicio.cambiarPasswordPropia("Mala", "NuevaSegura12345"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("actual no es correcta");

        assertThat(usuario.getHashPassword()).isEqualTo("$2a$viejo");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("super_admin (tenant NULL) cambia su contrasena SIN fijar ningun tenant (usuario_login_mutacion)")
    void cambiarPasswordSuperAdminNoFijaTenant() {
        // El super_admin tiene tenant NULL; su fila se crea por reflexion porque
        // Usuario.crear exige tenant no nulo (cuenta de empresa).
        Usuario superAdmin = usuarioEmpresa(TENANT, "root@plataforma", "$2a$viejo");
        establecerTenantNulo(superAdmin);
        autenticarComo(superAdmin.getId(), null);
        when(usuarioRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(passwordEncoder.matches("Actual123", "$2a$viejo")).thenReturn(true);
        when(passwordEncoder.encode("NuevaSegura12345")).thenReturn("$2a$nuevo");

        servicio.cambiarPasswordPropia("Actual123", "NuevaSegura12345");

        assertThat(superAdmin.getHashPassword()).isEqualTo("$2a$nuevo");
        // Nunca se fija un tenant para el super_admin (tenant NULL).
        verify(tenantSession, never()).applyTenant(any());
        verify(usuarioRepository).save(any(Usuario.class));
    }

    private static void establecerTenantNulo(Usuario usuario) {
        try {
            var f = Usuario.class.getDeclaredField("tenantId");
            f.setAccessible(true);
            f.set(usuario, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo anular tenantId", e);
        }
    }
}
