package com.dessti.crm.platform.security.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.roles.Rol;
import com.dessti.crm.platform.security.roles.RolRepository;
import com.dessti.crm.platform.security.sesiones.MotivoRevocacion;
import com.dessti.crm.platform.security.sesiones.RegistroSesionesPort;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioUsuarios} (Req 4) centradas en las reglas
 * criticas de la gestion de cuentas de acceso:
 *
 * <ul>
 *   <li>Creacion valida: persiste con contrasena cifrada y audita (Req 4.1, 4.5).</li>
 *   <li>Identificador de acceso duplicado da conflicto 409, por comprobacion
 *       previa y por violacion del indice de la BD (Req 4.4).</li>
 *   <li>Desactivar una cuenta inexistente en la Empresa da 404 (Req 4.2, 23.3).</li>
 *   <li>Asignacion de Roles valida reemplaza el conjunto y audita (Req 4.3).</li>
 *   <li>Rechazo de un Rol de nivel plataforma / super_admin (Req 27.7).</li>
 *   <li>Rechazo de un Rol inexistente (Req 23).</li>
 * </ul>
 *
 * <p>No usa {@code @SpringBootTest} ni Testcontainers: los colaboradores
 * (repositorios, {@link PasswordEncoder} y puerto de auditoria) se sustituyen
 * por dobles de Mockito.</p>
 */
class ServicioUsuariosTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTRO_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UsuarioRepository usuarioRepository;
    private RolRepository rolRepository;
    private PasswordEncoder passwordEncoder;
    private AuditoriaPort auditoria;
    private RegistroSesionesPort registroSesiones;
    private LimiteUsuariosPort limiteUsuarios;
    private com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort modulosHabilitados;
    private ServicioUsuarios servicio;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        rolRepository = mock(RolRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditoria = mock(AuditoriaPort.class);
        registroSesiones = mock(RegistroSesionesPort.class);
        limiteUsuarios = mock(LimiteUsuariosPort.class);
        modulosHabilitados = mock(com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort.class);
        // Por defecto la Empresa tiene cupo en su Plan (Req 25.3): las pruebas
        // que ejercen el limite lo sobrescriben explicitamente.
        when(limiteUsuarios.puedeCrearUsuario(any())).thenReturn(true);
        // Por defecto la Empresa contrata TODOS los modulos que usan los roles de
        // las pruebas (ventas->comercial, contabilidad->facturacion, etc.), para
        // que el gating por modulo no interfiera salvo donde se ejerce explicito.
        when(modulosHabilitados.modulosHabilitadosDe(any())).thenReturn(java.util.List.of(
                "comercial", "operacion", "compras", "inventario-avanzado", "mantenimiento",
                "facturacion", "contabilidad", "tesoreria", "activos-fijos", "rh-nomina",
                "redes-sociales"));
        servicio = new ServicioUsuarios(
                usuarioRepository, rolRepository, passwordEncoder, auditoria,
                registroSesiones, limiteUsuarios, modulosHabilitados);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    /** Doble de {@link Rol} con id, nombre y tenant controlados. */
    private static Rol rol(UUID id, String nombre, UUID tenantId) {
        Rol r = mock(Rol.class);
        when(r.getId()).thenReturn(id);
        when(r.getNombre()).thenReturn(nombre);
        when(r.getTenantId()).thenReturn(tenantId);
        return r;
    }

    // -----------------------------------------------------------------
    // Req 4.1 + 4.5: creacion valida persiste (cifrada) y audita
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Crea un Usuario valido, cifra la contrasena, lo persiste y audita (Req 4.1, 4.5)")
    void creaUsuarioValidoYAudita() {
        UUID rolId = UUID.randomUUID();
        Rol ventas = rol(rolId, "ventas", null); // predefinido de empresa (tenant NULL)
        when(rolRepository.findById(rolId)).thenReturn(Optional.of(ventas));
        when(usuarioRepository.existsByIdentificadorAcceso("jperez")).thenReturn(false);
        when(passwordEncoder.encode("secreto-123")).thenReturn("$2a$hash");
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new CrearUsuarioCommand("jperez", "secreto-123", "Juan Perez", Set.of(rolId));

        UsuarioDto dto = servicio.crearUsuario(comando);

        assertThat(dto.id()).isNotNull();
        assertThat(dto.identificadorAcceso()).isEqualTo("jperez");
        assertThat(dto.nombreVisible()).isEqualTo("Juan Perez");
        assertThat(dto.activo()).isTrue();
        assertThat(dto.roles()).extracting(UsuarioDto.RolAsignadoDto::nombre).containsExactly("ventas");

        // La contrasena se cifra (nunca en claro).
        verify(passwordEncoder).encode("secreto-123");
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario persistido = usuarioCaptor.getValue();
        assertThat(persistido.getTenantId()).isEqualTo(TENANT);
        assertThat(persistido.isActivo()).isTrue();
        assertThat(persistido.getHashPassword()).isEqualTo("$2a$hash");

        // Auditoria sin secretos (Req 4.5, 10.10).
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.tenantId()).contains(TENANT);
        assertThat(evento.accion()).isEqualTo("crear");
        assertThat(evento.recurso()).isEqualTo(ServicioUsuarios.RECURSO_USUARIO);
        assertThat(evento.detalle()).doesNotContain("secreto-123", "$2a$hash");
    }

    // -----------------------------------------------------------------
    // Req 4.4: identificador de acceso duplicado -> 409
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Rechaza crear un Usuario con identificador de acceso ya existente (Req 4.4)")
    void rechazaIdentificadorDuplicadoPorComprobacionPrevia() {
        UUID rolId = UUID.randomUUID();
        Rol ventas = rol(rolId, "ventas", null);
        when(rolRepository.findById(rolId)).thenReturn(Optional.of(ventas));
        when(usuarioRepository.existsByIdentificadorAcceso("jperez")).thenReturn(true);

        var comando = new CrearUsuarioCommand("jperez", "secreto-123", null, Set.of(rolId));

        assertThatThrownBy(() -> servicio.crearUsuario(comando))
                .isInstanceOf(ConflictoUnicidadException.class);

        verify(usuarioRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("Traduce la violacion del indice unico global a 409 (Req 4.4)")
    void traduceViolacionUnicidadDeLaBaseDeDatos() {
        UUID rolId = UUID.randomUUID();
        Rol ventas = rol(rolId, "ventas", null);
        when(rolRepository.findById(rolId)).thenReturn(Optional.of(ventas));
        when(usuarioRepository.existsByIdentificadorAcceso("jperez")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(usuarioRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uq_usuario_identificador_acceso"));

        var comando = new CrearUsuarioCommand("jperez", "secreto-123", null, Set.of(rolId));

        assertThatThrownBy(() -> servicio.crearUsuario(comando))
                .isInstanceOf(ConflictoUnicidadException.class);
    }

    // -----------------------------------------------------------------
    // Req 25.3: limite de Usuarios del Plan
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Rechaza crear un Usuario cuando la Empresa alcanzo el limite de su Plan -> 422 (Req 25.3)")
    void rechazaCrearUsuarioCuandoSeAlcanzaElLimiteDelPlan() {
        UUID rolId = UUID.randomUUID();
        Rol ventas = rol(rolId, "ventas", null);
        when(rolRepository.findById(rolId)).thenReturn(Optional.of(ventas));
        // La Empresa ya no tiene cupo en su Plan.
        when(limiteUsuarios.puedeCrearUsuario(TENANT)).thenReturn(false);

        var comando = new CrearUsuarioCommand("jperez", "secreto-123", null, Set.of(rolId));

        assertThatThrownBy(() -> servicio.crearUsuario(comando))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("limite");

        // No se persiste ni se audita cuando se rechaza por limite.
        verify(usuarioRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("Permite crear un Usuario cuando la Empresa aun tiene cupo en su Plan (Req 25.3)")
    void permiteCrearUsuarioBajoElLimiteDelPlan() {
        UUID rolId = UUID.randomUUID();
        Rol ventas = rol(rolId, "ventas", null);
        when(rolRepository.findById(rolId)).thenReturn(Optional.of(ventas));
        when(limiteUsuarios.puedeCrearUsuario(TENANT)).thenReturn(true);
        when(usuarioRepository.existsByIdentificadorAcceso("jperez")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new CrearUsuarioCommand("jperez", "secreto-123", null, Set.of(rolId));

        UsuarioDto dto = servicio.crearUsuario(comando);

        assertThat(dto.identificadorAcceso()).isEqualTo("jperez");
        verify(usuarioRepository).save(any());
        verify(limiteUsuarios).puedeCrearUsuario(TENANT);
    }

    // -----------------------------------------------------------------
    // Req 4.2 + 23.3: desactivacion
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Desactiva una cuenta de la Empresa y audita (Req 4.2, 4.5)")
    void desactivaUsuarioDeLaEmpresa() {
        UUID usuarioId = UUID.randomUUID();
        Rol ventas = rol(UUID.randomUUID(), "ventas", null);
        Usuario existente = Usuario.crear(TENANT, "jperez", "$2a$hash", null, Set.of(ventas), "admin");
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UsuarioDto dto = servicio.desactivarUsuario(usuarioId);

        assertThat(dto.activo()).isFalse();
        assertThat(existente.isActivo()).isFalse();

        // La desactivacion revoca las Sesiones vigentes de la cuenta (Req 68.2).
        verify(registroSesiones).revocarTodasDeUsuario(usuarioId, MotivoRevocacion.DESACTIVACION);

        // Audita la desactivacion y la revocacion de sesiones derivada.
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(2)).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getAllValues()).extracting(EventoAuditoria::accion)
                .containsExactly("desactivar", "revocar_sesiones");
    }

    @Test
    @DisplayName("Un administrador revoca todas las sesiones de una cuenta y audita (Req 68.2)")
    void revocaSesionesDeUsuarioComoAdministrador() {
        UUID usuarioId = UUID.randomUUID();
        Rol ventas = rol(UUID.randomUUID(), "ventas", null);
        Usuario existente = Usuario.crear(TENANT, "jperez", "$2a$hash", null, Set.of(ventas), "admin");
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.of(existente));
        when(registroSesiones.revocarTodasDeUsuario(usuarioId, MotivoRevocacion.ADMINISTRADOR)).thenReturn(2);

        int revocadas = servicio.revocarSesiones(usuarioId);

        assertThat(revocadas).isEqualTo(2);
        verify(registroSesiones).revocarTodasDeUsuario(usuarioId, MotivoRevocacion.ADMINISTRADOR);
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo("revocar_sesiones");
    }

    @Test
    @DisplayName("Revocar sesiones de una cuenta inexistente en la Empresa da 404 (Req 23.3)")
    void revocarSesionesDeUsuarioInexistenteEsNoEncontrado() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.revocarSesiones(usuarioId))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(registroSesiones, never()).revocarTodasDeUsuario(any(), any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("Devuelve 404 al desactivar una cuenta inexistente en la Empresa (Req 23.3)")
    void desactivarUsuarioInexistenteEsNoEncontrado() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.desactivarUsuario(usuarioId))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(usuarioRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    // -----------------------------------------------------------------
    // Req 4.3: asignacion de Roles
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Asigna Roles validos a una cuenta, reemplaza el conjunto y audita (Req 4.3)")
    void asignaRolesValidos() {
        UUID usuarioId = UUID.randomUUID();
        Rol rolInicial = rol(UUID.randomUUID(), "ventas", null);
        Usuario existente = Usuario.crear(TENANT, "jperez", "$2a$hash", null, Set.of(rolInicial), "admin");
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID rolPredefinido = UUID.randomUUID();
        UUID rolPersonalizado = UUID.randomUUID();
        Rol contabilidad = rol(rolPredefinido, "contabilidad", null);
        Rol personalizado = rol(rolPersonalizado, "comercial-junior", TENANT);
        when(rolRepository.findById(rolPredefinido)).thenReturn(Optional.of(contabilidad));
        when(rolRepository.findById(rolPersonalizado)).thenReturn(Optional.of(personalizado));

        UsuarioDto dto = servicio.asignarRoles(usuarioId, Set.of(rolPredefinido, rolPersonalizado));

        assertThat(dto.roles()).extracting(UsuarioDto.RolAsignadoDto::nombre)
                .containsExactlyInAnyOrder("contabilidad", "comercial-junior");
        assertThat(existente.getRoles()).hasSize(2);
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo("asignar_roles");
    }

    @Test
    @DisplayName("Rechaza asignar un Rol de nivel plataforma (super_admin) a un usuario de empresa (Req 27.7)")
    void rechazaRolDePlataforma() {
        UUID usuarioId = UUID.randomUUID();
        Rol rolInicial = rol(UUID.randomUUID(), "ventas", null);
        Usuario existente = Usuario.crear(TENANT, "jperez", "$2a$hash", null, Set.of(rolInicial), "admin");
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.of(existente));

        UUID superAdminId = UUID.randomUUID();
        Rol superAdmin = rol(superAdminId, "super_admin", null);
        when(rolRepository.findById(superAdminId)).thenReturn(Optional.of(superAdmin));

        assertThatThrownBy(() -> servicio.asignarRoles(usuarioId, Set.of(superAdminId)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("plataforma");

        verify(usuarioRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("Rechaza asignar un Rol inexistente (Req 23)")
    void rechazaRolInexistente() {
        UUID usuarioId = UUID.randomUUID();
        Rol rolInicial = rol(UUID.randomUUID(), "ventas", null);
        Usuario existente = Usuario.crear(TENANT, "jperez", "$2a$hash", null, Set.of(rolInicial), "admin");
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.of(existente));

        UUID inexistente = UUID.randomUUID();
        when(rolRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarRoles(usuarioId, Set.of(inexistente)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza asignar un Rol personalizado de otra Empresa -> 404 (Req 23.3)")
    void rechazaRolDeOtraEmpresa() {
        UUID usuarioId = UUID.randomUUID();
        Rol rolInicial = rol(UUID.randomUUID(), "ventas", null);
        Usuario existente = Usuario.crear(TENANT, "jperez", "$2a$hash", null, Set.of(rolInicial), "admin");
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.of(existente));

        UUID rolAjeno = UUID.randomUUID();
        Rol ajeno = rol(rolAjeno, "rol-ajeno", OTRO_TENANT);
        when(rolRepository.findById(rolAjeno)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> servicio.asignarRoles(usuarioId, Set.of(rolAjeno)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Rechaza asignar un conjunto vacio de Roles (toda cuenta requiere al menos uno, Req 4.1)")
    void rechazaSinRoles() {
        UUID usuarioId = UUID.randomUUID();
        Usuario existente = Usuario.crear(TENANT, "jperez", "$2a$hash", null,
                Set.of(rol(UUID.randomUUID(), "ventas", null)), "admin");
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servicio.asignarRoles(usuarioId, Set.of()))
                .isInstanceOf(ReglaNegocioException.class);

        verify(usuarioRepository, never()).save(any());
    }

    // -----------------------------------------------------------------
    // Gating por modulo contratado (plataforma-multigiro): defensa en profundidad
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Rechaza asignar un rol de modulo cuyo modulo NO contrato la Empresa -> 422")
    void rechazaRolDeModuloNoContratado() {
        UUID usuarioId = UUID.randomUUID();
        Rol rolInicial = rol(UUID.randomUUID(), "gerente", null);
        Usuario existente = Usuario.crear(TENANT, "jperez", "$2a$hash", null, Set.of(rolInicial), "admin");
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.of(existente));

        // La Empresa solo contrata 'estrategia' (que no habilita a 'ventas').
        when(modulosHabilitados.modulosHabilitadosDe(TENANT)).thenReturn(java.util.List.of("estrategia"));

        UUID ventasId = UUID.randomUUID();
        Rol ventas = rol(ventasId, "ventas", null); // predefinido, requiere 'comercial'
        when(rolRepository.findById(ventasId)).thenReturn(Optional.of(ventas));

        assertThatThrownBy(() -> servicio.asignarRoles(usuarioId, Set.of(ventasId)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("modulo");

        verify(usuarioRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("Permite asignar 'ventas' cuando la Empresa contrato 'comercial' (gating por modulo)")
    void permiteRolDeModuloContratado() {
        UUID usuarioId = UUID.randomUUID();
        Rol rolInicial = rol(UUID.randomUUID(), "gerente", null);
        Usuario existente = Usuario.crear(TENANT, "jperez", "$2a$hash", null, Set.of(rolInicial), "admin");
        when(usuarioRepository.findByIdAndTenantId(usuarioId, TENANT)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // La Empresa contrata 'comercial', que habilita 'ventas'.
        when(modulosHabilitados.modulosHabilitadosDe(TENANT)).thenReturn(java.util.List.of("comercial"));

        UUID ventasId = UUID.randomUUID();
        Rol ventas = rol(ventasId, "ventas", null);
        when(rolRepository.findById(ventasId)).thenReturn(Optional.of(ventas));

        UsuarioDto dto = servicio.asignarRoles(usuarioId, Set.of(ventasId));

        assertThat(dto.roles()).extracting(UsuarioDto.RolAsignadoDto::nombre).containsExactly("ventas");
        verify(usuarioRepository).save(any());
    }
}
