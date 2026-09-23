package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.giros.adapter.out.persistence.GiroRepository;
import com.dessti.crm.platform.giros.domain.Giro;
import com.dessti.crm.platform.security.roles.Rol;
import com.dessti.crm.platform.security.roles.RolRepository;
import com.dessti.crm.platform.security.sesiones.MotivoRevocacion;
import com.dessti.crm.platform.security.sesiones.RegistroSesionesPort;
import com.dessti.crm.platform.security.usuarios.Usuario;
import com.dessti.crm.platform.security.usuarios.UsuarioRepository;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas unitarias de {@link ServicioEmpresas} (Req 24). Usan dobles de
 * Mockito y un {@link Clock} fijo; no arrancan contexto de Spring ni base de
 * datos. Verifican el aprovisionamiento del alta (Empresa + admin_empresa +
 * suscripcion + auditoria, Req 24.2/24.6), el conflicto de RFC duplicado
 * (Req 24.2 -> 409), la suspension (Req 24.4) y el listado paginado filtrable
 * (Req 24.5).
 */
class ServicioEmpresasTest {

    private static final Instant T0 = Instant.parse("2025-03-10T12:00:00Z");
    private static final UUID PLAN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROL_ADMIN_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    /** Giro de prueba (Req 2.1); en produccion lo aporta/valida la tarea 4.3. */
    private static final UUID GIRO_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

    private EmpresaRepository empresaRepository;
    private GiroRepository giroRepository;
    private PlanRepository planRepository;
    private PaqueteSuscripcionRepository paqueteSuscripcionRepository;
    private SuscripcionRepository suscripcionRepository;
    private UsuarioRepository usuarioRepository;
    private RolRepository rolRepository;
    private PasswordEncoder passwordEncoder;
    private DatosVerticalPort datosVertical;
    private AuditoriaPort auditoria;
    private RegistroSesionesPort registroSesiones;
    private ContratacionProperties contratacionProperties;
    private TenantSessionInitializer tenantSession;
    private ServicioEmpresas servicio;

    @BeforeEach
    void setUp() {
        empresaRepository = mock(EmpresaRepository.class);
        giroRepository = mock(GiroRepository.class);
        planRepository = mock(PlanRepository.class);
        paqueteSuscripcionRepository = mock(PaqueteSuscripcionRepository.class);
        suscripcionRepository = mock(SuscripcionRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        rolRepository = mock(RolRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        datosVertical = mock(DatosVerticalPort.class);
        auditoria = mock(AuditoriaPort.class);
        registroSesiones = mock(RegistroSesionesPort.class);
        // Umbral de aviso real por defecto (14 dias): valor simple, no requiere doble.
        contratacionProperties = new ContratacionProperties(14);
        tenantSession = mock(TenantSessionInitializer.class);
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        servicio = new ServicioEmpresas(empresaRepository, giroRepository, planRepository,
                paqueteSuscripcionRepository, suscripcionRepository, usuarioRepository, rolRepository,
                passwordEncoder, datosVertical, auditoria, registroSesiones, contratacionProperties,
                clock, tenantSession);
    }

    private CrearEmpresaCommand comandoValido(String adminPassword) {
        return new CrearEmpresaCommand("Anuncios del Norte", "ANO120101AB1", GIRO_ID, PLAN_ID,
                null, false, "admin@anuncios.com", adminPassword, null, null);
    }

    private void stubAltaFeliz() {
        when(giroRepository.findById(GIRO_ID)).thenReturn(Optional.of(giroActivo(GIRO_ID)));
        when(empresaRepository.existsByRfc("ANO120101AB1")).thenReturn(false);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(planFalso(PLAN_ID)));
        when(usuarioRepository.existsByIdentificadorAcceso("admin@anuncios.com")).thenReturn(false);
        when(rolRepository.findByNombreAndTenantIdIsNull("admin_empresa"))
                .thenReturn(Optional.of(rolFalso(ROL_ADMIN_ID, "admin_empresa", null)));
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        // saveAndFlush devuelve la misma entidad recibida.
        when(empresaRepository.saveAndFlush(any(Empresa.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("crearEmpresa crea la Empresa activa, el admin_empresa, la suscripcion y audita (Req 24.2, 24.6)")
    void crearEmpresaAprovisionaTodo() {
        stubAltaFeliz();

        EmpresaCreadaDto resultado = servicio.crearEmpresa(comandoValido("Secreta12345"));

        // Empresa activa con RFC normalizado a mayusculas.
        assertThat(resultado.empresa().estado()).isEqualTo(EstadoEmpresa.ACTIVA);
        assertThat(resultado.empresa().rfc()).isEqualTo("ANO120101AB1");
        assertThat(resultado.empresa().id()).isNotNull();

        // Se persiste la Empresa, el admin_empresa y la suscripcion.
        ArgumentCaptor<Empresa> empresaCaptor = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).saveAndFlush(empresaCaptor.capture());
        UUID tenantId = empresaCaptor.getValue().getId();

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(usuarioCaptor.capture());
        Usuario admin = usuarioCaptor.getValue();
        assertThat(admin.getTenantId()).isEqualTo(tenantId);
        assertThat(admin.getIdentificadorAcceso()).isEqualTo("admin@anuncios.com");
        assertThat(admin.getRoles()).extracting(Rol::getNombre).containsExactly("admin_empresa");
        // La contrasena proporcionada se cifra; nunca se persiste en claro.
        assertThat(admin.getHashPassword()).isEqualTo("$2a$hash");

        ArgumentCaptor<Suscripcion> suscCaptor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository).save(suscCaptor.capture());
        Suscripcion suscripcion = suscCaptor.getValue();
        assertThat(suscripcion.getTenantId()).isEqualTo(tenantId);
        assertThat(suscripcion.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(suscripcion.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(suscripcion.getVigenciaInicio()).isEqualTo(java.time.LocalDate.of(2025, 3, 10));

        // Auditoria de plataforma (Req 24.6): accion 'crear', recurso 'empresa',
        // sin la contrasena en claro (Req 10.10).
        ArgumentCaptor<EventoAuditoria> evCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evCaptor.capture());
        EventoAuditoria evento = evCaptor.getValue();
        assertThat(evento.accion()).isEqualTo("crear");
        assertThat(evento.recurso()).isEqualTo("empresa");
        assertThat(evento.tenantId()).isEmpty();
        assertThat(evento.detalle()).doesNotContain("Secreta12345");
        // El detalle del alta incluye el Giro seleccionado (Req 2.5).
        assertThat(evento.detalle()).contains("anuncios-luminosos");

        // Contrasena proporcionada: no se devuelve temporal.
        assertThat(resultado.adminPasswordTemporal()).isNull();
    }

    @Test
    @DisplayName("crearEmpresa fija app.current_tenant al nuevo tenant antes de guardar el admin_empresa (RLS V48 WITH CHECK)")
    void crearEmpresaFijaTenantAntesDeGuardarAdmin() {
        // given
        stubAltaFeliz();

        // when
        servicio.crearEmpresa(comandoValido("Secreta12345"));

        // then: se fija el tenant de sesion con la PK de la Empresa recien creada.
        ArgumentCaptor<Empresa> empresaCaptor = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).saveAndFlush(empresaCaptor.capture());
        UUID tenantId = empresaCaptor.getValue().getId();
        verify(tenantSession).applyTenant(tenantId);

        // El orden es imprescindible: primero se guarda la Empresa, luego se fija
        // app.current_tenant al nuevo tenant y solo despues se inserta el
        // admin_empresa, de modo que la politica RLS WITH CHECK de V48 (que exige
        // tenant_id = current_setting('app.current_tenant')) se satisfaga.
        InOrder orden = inOrder(empresaRepository, tenantSession, usuarioRepository);
        orden.verify(empresaRepository).saveAndFlush(any(Empresa.class));
        orden.verify(tenantSession).applyTenant(tenantId);
        orden.verify(usuarioRepository).saveAndFlush(any(Usuario.class));
    }

    @Test
    @DisplayName("crearEmpresa sin contrasena genera una temporal y la devuelve una unica vez (Req 11.3)")
    void crearEmpresaGeneraPasswordTemporal() {
        stubAltaFeliz();

        EmpresaCreadaDto resultado = servicio.crearEmpresa(comandoValido(null));

        assertThat(resultado.adminPasswordTemporal()).isNotBlank();
        // La contrasena temporal se cifra antes de persistir (encode fue invocado).
        verify(passwordEncoder).encode(any());
    }

    @Test
    @DisplayName("crearEmpresa con RFC duplicado lanza ConflictoUnicidadException (409) y no persiste nada (Req 24.2)")
    void crearEmpresaRfcDuplicado() {
        when(empresaRepository.existsByRfc("ANO120101AB1")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crearEmpresa(comandoValido("Secreta12345")))
                .isInstanceOf(ConflictoUnicidadException.class);

        verify(empresaRepository, never()).saveAndFlush(any());
        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("crearEmpresa con Plan inicial inexistente lanza RecursoNoEncontradoException (404) (Req 24.2)")
    void crearEmpresaPlanInexistente() {
        when(giroRepository.findById(GIRO_ID)).thenReturn(Optional.of(giroActivo(GIRO_ID)));
        when(empresaRepository.existsByRfc("ANO120101AB1")).thenReturn(false);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crearEmpresa(comandoValido("Secreta12345")))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(empresaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("crearEmpresa con Giro inexistente lanza ReglaNegocioException (422) y no persiste nada (Req 2.2)")
    void crearEmpresaGiroInexistente() {
        when(giroRepository.findById(GIRO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crearEmpresa(comandoValido("Secreta12345")))
                .isInstanceOf(ReglaNegocioException.class);

        verify(empresaRepository, never()).saveAndFlush(any());
        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("crearEmpresa con Giro inactivo lanza ReglaNegocioException (422) y no persiste nada (Req 2.2)")
    void crearEmpresaGiroInactivo() {
        Giro inactivo = giroActivo(GIRO_ID);
        set(inactivo, "activo", false);
        when(giroRepository.findById(GIRO_ID)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> servicio.crearEmpresa(comandoValido("Secreta12345")))
                .isInstanceOf(ReglaNegocioException.class);

        verify(empresaRepository, never()).saveAndFlush(any());
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("suspenderEmpresa fija estado 'suspendida' y audita (Req 24.4, 24.6)")
    void suspenderEmpresaFijaEstadoYAudita() {
        Empresa empresa = Empresa.crear("Anuncios del Sur", "ASU120101AB1", GIRO_ID, "super");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        EmpresaDto dto = servicio.suspenderEmpresa(empresa.getId());

        assertThat(dto.estado()).isEqualTo(EstadoEmpresa.SUSPENDIDA);
        ArgumentCaptor<EventoAuditoria> evCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evCaptor.capture());
        assertThat(evCaptor.getValue().accion()).isEqualTo("suspender");
        assertThat(evCaptor.getValue().recurso()).isEqualTo("empresa");
    }

    @Test
    @DisplayName("activarEmpresa vuelve a estado 'activa' y audita (Req 24.1)")
    void activarEmpresaFijaEstado() {
        Empresa empresa = Empresa.crear("Anuncios del Este", "AES120101AB1", GIRO_ID, "super");
        empresa.suspender("super");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        EmpresaDto dto = servicio.activarEmpresa(empresa.getId());

        assertThat(dto.estado()).isEqualTo(EstadoEmpresa.ACTIVA);
        verify(auditoria).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("suspenderEmpresa inexistente lanza RecursoNoEncontradoException (404)")
    void suspenderEmpresaInexistente() {
        UUID id = UUID.randomUUID();
        when(empresaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.suspenderEmpresa(id))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("listarEmpresas con estado filtra por estado; sin estado lista todas (Req 24.5)")
    void listarEmpresasFiltraPorEstado() {
        Pageable pageable = PageRequest.of(0, 20);
        Empresa activa = Empresa.crear("Activa SA", "ACT120101AB1", GIRO_ID, "super");
        Page<Empresa> pagina = new PageImpl<>(List.of(activa), pageable, 1);
        when(empresaRepository.findByEstado(EstadoEmpresa.ACTIVA, pageable)).thenReturn(pagina);
        when(empresaRepository.findAll(pageable)).thenReturn(pagina);

        Page<Empresa> conFiltro = servicio.listarEmpresas(EstadoEmpresa.ACTIVA, null, pageable);
        assertThat(conFiltro.getContent()).containsExactly(activa);
        verify(empresaRepository).findByEstado(EstadoEmpresa.ACTIVA, pageable);

        Page<Empresa> sinFiltro = servicio.listarEmpresas(null, null, pageable);
        assertThat(sinFiltro.getContent()).containsExactly(activa);
        verify(empresaRepository).findAll(eq(pageable));
    }

    @Test
    @DisplayName("listarEmpresas con q en blanco ignora la busqueda (comportamiento historico, Req 24.5)")
    void listarEmpresasQEnBlancoIgnoraBusqueda() {
        Pageable pageable = PageRequest.of(0, 20);
        Empresa activa = Empresa.crear("Activa SA", "ACT120101AB1", GIRO_ID, "super");
        Page<Empresa> pagina = new PageImpl<>(List.of(activa), pageable, 1);
        when(empresaRepository.findAll(pageable)).thenReturn(pagina);

        // q en blanco -> se comporta como listado sin busqueda (no invoca buscar).
        Page<Empresa> resultado = servicio.listarEmpresas(null, "   ", pageable);
        assertThat(resultado.getContent()).containsExactly(activa);
        verify(empresaRepository).findAll(eq(pageable));
        verify(empresaRepository, never()).buscar(any(), any());
        verify(empresaRepository, never()).buscarPorEstado(any(), any(), any());
    }

    @Test
    @DisplayName("listarEmpresas con q y sin estado delega en buscar recortando el texto (Req 24)")
    void listarEmpresasBuscaSinEstado() {
        Pageable pageable = PageRequest.of(0, 20);
        Empresa activa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        Page<Empresa> pagina = new PageImpl<>(List.of(activa), pageable, 1);
        when(empresaRepository.buscar("norte", pageable)).thenReturn(pagina);

        // El texto se recorta antes de delegar en el repositorio.
        Page<Empresa> resultado = servicio.listarEmpresas(null, "  norte  ", pageable);
        assertThat(resultado.getContent()).containsExactly(activa);
        verify(empresaRepository).buscar("norte", pageable);
        verify(empresaRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("listarEmpresas con q y estado delega en buscarPorEstado (Req 24)")
    void listarEmpresasBuscaPorEstado() {
        Pageable pageable = PageRequest.of(0, 20);
        Empresa activa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        Page<Empresa> pagina = new PageImpl<>(List.of(activa), pageable, 1);
        when(empresaRepository.buscarPorEstado(EstadoEmpresa.ACTIVA, "ano", pageable))
                .thenReturn(pagina);

        Page<Empresa> resultado = servicio.listarEmpresas(EstadoEmpresa.ACTIVA, "ano", pageable);
        assertThat(resultado.getContent()).containsExactly(activa);
        verify(empresaRepository).buscarPorEstado(EstadoEmpresa.ACTIVA, "ano", pageable);
        verify(empresaRepository, never()).buscar(any(), any());
    }

    @Test
    @DisplayName("crearEmpresa asigna los datos descriptivos antes de guardar la Empresa (Req 24)")
    void crearEmpresaAsignaDatosDescriptivos() {
        stubAltaFeliz();
        DatosDescriptivosEmpresa datos = new DatosDescriptivosEmpresa(
                "Marca Norte", "  Contacto@Norte.MX ", "555-1234", "https://norte.mx",
                "Calle 1", "Monterrey", "Nuevo Leon", "64000", "Mexico",
                "cliente clave", "https://cdn.example.com/logo.png");
        var comando = new CrearEmpresaCommand("Anuncios del Norte", "ANO120101AB1", GIRO_ID,
                PLAN_ID, null, false, "admin@anuncios.com", "Secreta12345", null, datos);

        EmpresaCreadaDto resultado = servicio.crearEmpresa(comando);

        // El DTO devuelto refleja los datos descriptivos normalizados.
        assertThat(resultado.empresa().nombreComercial()).isEqualTo("Marca Norte");
        // El correo se normaliza a minusculas y se recorta.
        assertThat(resultado.empresa().emailContacto()).isEqualTo("contacto@norte.mx");
        assertThat(resultado.empresa().direccion().ciudad()).isEqualTo("Monterrey");
        assertThat(resultado.empresa().brandingLogo()).isEqualTo("https://cdn.example.com/logo.png");

        // La Empresa persistida lleva ya los datos descriptivos (mismo insert).
        ArgumentCaptor<Empresa> empresaCaptor = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).saveAndFlush(empresaCaptor.capture());
        assertThat(empresaCaptor.getValue().getNombreComercial()).isEqualTo("Marca Norte");
        assertThat(empresaCaptor.getValue().getEmailContacto()).isEqualTo("contacto@norte.mx");
    }

    @Test
    @DisplayName("crearEmpresa con RFC mal formado lanza ReglaNegocioException (422) y no persiste (Req 24)")
    void crearEmpresaRfcMalFormado() {
        var comando = new CrearEmpresaCommand("Anuncios del Norte", "RFC-INVALIDO", GIRO_ID,
                PLAN_ID, null, false, "admin@anuncios.com", "Secreta12345", null, null);

        assertThatThrownBy(() -> servicio.crearEmpresa(comando))
                .isInstanceOf(ReglaNegocioException.class);

        verify(empresaRepository, never()).existsByRfc(any());
        verify(empresaRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------
    // cambiarGiro (Req 3): operacion controlada de cambio de Giro.
    // ------------------------------------------------------------------

    /** Id del nuevo Giro destino en las pruebas de cambio (Req 3.1). */
    private static final UUID NUEVO_GIRO_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");

    @Test
    @DisplayName("cambiarGiro sin datos del vertical actual cambia el Giro y audita anterior/nuevo (Req 3.1, 3.3)")
    void cambiarGiroSinDatosCambiaYAudita() {
        Empresa empresa = Empresa.crear("Anuncios del Centro", "ACE120101AB1", GIRO_ID, "super");
        Giro nuevoGiro = giroConClave(NUEVO_GIRO_ID, "manufactura");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(giroRepository.findById(NUEVO_GIRO_ID)).thenReturn(Optional.of(nuevoGiro));
        when(giroRepository.findById(GIRO_ID)).thenReturn(Optional.of(giroActivo(GIRO_ID)));
        when(datosVertical.tieneDatosDeVertical(empresa.getId(), "anuncios-luminosos"))
                .thenReturn(false);
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        EmpresaDto dto = servicio.cambiarGiro(empresa.getId(), NUEVO_GIRO_ID);

        // Se devuelve el DTO de la Empresa afectada (Req 3.1).
        assertThat(dto.id()).isEqualTo(empresa.getId());
        // El Giro de la Empresa quedo reasignado al nuevo Giro (Req 3.1).
        ArgumentCaptor<Empresa> empresaCaptor = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).save(empresaCaptor.capture());
        assertThat(empresaCaptor.getValue().getGiroId()).isEqualTo(NUEVO_GIRO_ID);

        // Auditoria (Req 3.3): accion 'cambiar_giro' sobre recurso 'empresa', con
        // el giro anterior y el nuevo en el detalle.
        ArgumentCaptor<EventoAuditoria> evCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evCaptor.capture());
        EventoAuditoria evento = evCaptor.getValue();
        assertThat(evento.accion()).isEqualTo("cambiar_giro");
        assertThat(evento.recurso()).isEqualTo("empresa");
        assertThat(evento.detalle()).contains("anuncios-luminosos");
        assertThat(evento.detalle()).contains("manufactura");
    }

    @Test
    @DisplayName("cambiarGiro con datos del vertical actual lanza ReglaNegocioException (422) y no cambia (Req 3.2)")
    void cambiarGiroConDatosRechaza() {
        Empresa empresa = Empresa.crear("Anuncios del Centro", "ACE120101AB1", GIRO_ID, "super");
        Giro nuevoGiro = giroConClave(NUEVO_GIRO_ID, "manufactura");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(giroRepository.findById(NUEVO_GIRO_ID)).thenReturn(Optional.of(nuevoGiro));
        when(giroRepository.findById(GIRO_ID)).thenReturn(Optional.of(giroActivo(GIRO_ID)));
        when(datosVertical.tieneDatosDeVertical(empresa.getId(), "anuncios-luminosos"))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.cambiarGiro(empresa.getId(), NUEVO_GIRO_ID))
                .isInstanceOf(ReglaNegocioException.class);

        // No se persiste el cambio ni se audita cuando hay datos del vertical.
        verify(empresaRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
        assertThat(empresa.getGiroId()).isEqualTo(GIRO_ID);
    }

    @Test
    @DisplayName("cambiarGiro a un Giro inactivo lanza ReglaNegocioException (422) y no cambia (Req 3.1)")
    void cambiarGiroNuevoInactivo() {
        Empresa empresa = Empresa.crear("Anuncios del Centro", "ACE120101AB1", GIRO_ID, "super");
        Giro inactivo = giroConClave(NUEVO_GIRO_ID, "manufactura");
        set(inactivo, "activo", false);
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(giroRepository.findById(NUEVO_GIRO_ID)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> servicio.cambiarGiro(empresa.getId(), NUEVO_GIRO_ID))
                .isInstanceOf(ReglaNegocioException.class);

        verify(empresaRepository, never()).save(any());
        assertThat(empresa.getGiroId()).isEqualTo(GIRO_ID);
    }

    @Test
    @DisplayName("cambiarGiro a un Giro inexistente lanza ReglaNegocioException (422) (Req 3.1)")
    void cambiarGiroNuevoInexistente() {
        Empresa empresa = Empresa.crear("Anuncios del Centro", "ACE120101AB1", GIRO_ID, "super");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(giroRepository.findById(NUEVO_GIRO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarGiro(empresa.getId(), NUEVO_GIRO_ID))
                .isInstanceOf(ReglaNegocioException.class);

        verify(empresaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cambiarGiro de una Empresa inexistente lanza RecursoNoEncontradoException (404)")
    void cambiarGiroEmpresaInexistente() {
        UUID id = UUID.randomUUID();
        when(empresaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarGiro(id, NUEVO_GIRO_ID))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(empresaRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // restablecerPasswordAdmin (CHANGE 3): reset de la contrasena del admin.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("reset con contrasena explicita fija el hash, revoca sesiones y devuelve passwordTemporal null")
    void resetConPasswordExplicita() {
        Empresa empresa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        Usuario admin = Usuario.crear(empresa.getId(), "admin@anuncios.com", "$2a$viejo", null,
                java.util.Set.of(rolFalso(ROL_ADMIN_ID, "admin_empresa", null)), "super");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(usuarioRepository.buscarPorTenantYRol(empresa.getId(), "admin_empresa"))
                .thenReturn(List.of(admin));
        when(passwordEncoder.encode("NuevaSegura12345")).thenReturn("$2a$nuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        ResetPasswordAdminDto dto = servicio.restablecerPasswordAdmin(
                empresa.getId(), "NuevaSegura12345", null);

        assertThat(dto.usuarioId()).isEqualTo(admin.getId());
        assertThat(dto.identificador()).isEqualTo("admin@anuncios.com");
        // Contrasena explicita => no se devuelve passwordTemporal (Req 11.3).
        assertThat(dto.passwordTemporal()).isNull();
        // El hash de la cuenta se reemplazo por el de la nueva contrasena.
        assertThat(admin.getHashPassword()).isEqualTo("$2a$nuevo");
        // Se revocan las sesiones vigentes del admin (Req 68.4).
        verify(registroSesiones).revocarTodasDeUsuario(admin.getId(), MotivoRevocacion.CAMBIO_PASSWORD);
    }

    @Test
    @DisplayName("reset sin contrasena genera una temporal no vacia, cambia el hash y la devuelve una vez (Req 11.3)")
    void resetSinPasswordGeneraTemporal() {
        Empresa empresa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        Usuario admin = Usuario.crear(empresa.getId(), "admin@anuncios.com", "$2a$viejo", null,
                java.util.Set.of(rolFalso(ROL_ADMIN_ID, "admin_empresa", null)), "super");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(usuarioRepository.buscarPorTenantYRol(empresa.getId(), "admin_empresa"))
                .thenReturn(List.of(admin));
        when(passwordEncoder.encode(any())).thenReturn("$2a$generado");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        ResetPasswordAdminDto dto = servicio.restablecerPasswordAdmin(empresa.getId(), null, null);

        assertThat(dto.passwordTemporal()).isNotBlank();
        assertThat(admin.getHashPassword()).isEqualTo("$2a$generado");
        verify(registroSesiones).revocarTodasDeUsuario(admin.getId(), MotivoRevocacion.CAMBIO_PASSWORD);
    }

    @Test
    @DisplayName("reset fija app.current_tenant a la Empresa ANTES de guardar el admin (RLS V48/V53)")
    void resetFijaTenantAntesDeGuardar() {
        Empresa empresa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        Usuario admin = Usuario.crear(empresa.getId(), "admin@anuncios.com", "$2a$viejo", null,
                java.util.Set.of(rolFalso(ROL_ADMIN_ID, "admin_empresa", null)), "super");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(usuarioRepository.buscarPorTenantYRol(empresa.getId(), "admin_empresa"))
                .thenReturn(List.of(admin));
        when(passwordEncoder.encode(any())).thenReturn("$2a$nuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        servicio.restablecerPasswordAdmin(empresa.getId(), "NuevaSegura12345", null);

        // El tenant de sesion se fija con la PK de la Empresa antes del save (RLS).
        InOrder orden = inOrder(tenantSession, usuarioRepository);
        orden.verify(tenantSession).applyTenant(empresa.getId());
        orden.verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    @DisplayName("reset con usuarioId de otra Empresa/inexistente lanza RecursoNoEncontradoException (404)")
    void resetUsuarioIdNoPerteneceALaEmpresa() {
        Empresa empresa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        UUID otroUsuario = UUID.randomUUID();
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findByIdAndTenantId(otroUsuario, empresa.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.restablecerPasswordAdmin(
                empresa.getId(), "NuevaSegura12345", otroUsuario))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("reset sin admin_empresa en la Empresa lanza RecursoNoEncontradoException (404)")
    void resetSinAdminEmpresa() {
        Empresa empresa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(usuarioRepository.buscarPorTenantYRol(empresa.getId(), "admin_empresa"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> servicio.restablecerPasswordAdmin(empresa.getId(), null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("reset de una Empresa inexistente lanza RecursoNoEncontradoException (404)")
    void resetEmpresaInexistente() {
        UUID id = UUID.randomUUID();
        when(empresaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.restablecerPasswordAdmin(id, null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(usuarioRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Tarea 3.4 (Req 3): regla de "suscripcion vigente" de una Empresa.
    // Se invoca directamente el metodo package-private estatico
    // ServicioEmpresas.suscripcionVigente(List<Suscripcion>).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("suscripcionVigente elige la ACTIVA aunque exista otra con vigenciaInicio mas reciente (Req 3.1)")
    void suscripcionVigenteEligeLaActiva() {
        UUID tenant = UUID.randomUUID();
        // Activa con inicio antiguo.
        Suscripcion activaVieja = suscripcion(tenant, PLAN_ID, EstadoSuscripcion.ACTIVA,
                LocalDate.of(2024, 1, 1), null, T0);
        // Suspendida con inicio mas reciente: NO debe ganar frente a la activa.
        Suscripcion suspendidaReciente = suscripcion(tenant, PLAN_ID, EstadoSuscripcion.SUSPENDIDA,
                LocalDate.of(2025, 6, 1), null, T0.plusSeconds(3600));

        Optional<Suscripcion> vigente = ServicioEmpresas.suscripcionVigente(
                List.of(suspendidaReciente, activaVieja));

        assertThat(vigente).containsSame(activaVieja);
    }

    @Test
    @DisplayName("suscripcionVigente sin activas elige la de vigenciaInicio mas reciente (Req 3.2)")
    void suscripcionVigenteSinActivasEligeInicioMasReciente() {
        UUID tenant = UUID.randomUUID();
        Suscripcion cancelViejo = suscripcion(tenant, PLAN_ID, EstadoSuscripcion.CANCELADA,
                LocalDate.of(2024, 1, 1), null, T0);
        Suscripcion suspendidaReciente = suscripcion(tenant, PLAN_ID, EstadoSuscripcion.SUSPENDIDA,
                LocalDate.of(2025, 2, 1), null, T0.plusSeconds(10));

        Optional<Suscripcion> vigente = ServicioEmpresas.suscripcionVigente(
                List.of(cancelViejo, suspendidaReciente));

        assertThat(vigente).containsSame(suspendidaReciente);
    }

    @Test
    @DisplayName("suscripcionVigente con empate en vigenciaInicio desempata por createdAt mas reciente (Req 3.3)")
    void suscripcionVigenteEmpateDesempataPorCreatedAt() {
        UUID tenant = UUID.randomUUID();
        LocalDate mismoInicio = LocalDate.of(2025, 2, 1);
        Suscripcion creadaAntes = suscripcion(tenant, PLAN_ID, EstadoSuscripcion.SUSPENDIDA,
                mismoInicio, null, T0);
        Suscripcion creadaDespues = suscripcion(tenant, PLAN_ID, EstadoSuscripcion.SUSPENDIDA,
                mismoInicio, null, T0.plusSeconds(3600));

        Optional<Suscripcion> vigente = ServicioEmpresas.suscripcionVigente(
                List.of(creadaAntes, creadaDespues));

        assertThat(vigente).containsSame(creadaDespues);
    }

    @Test
    @DisplayName("suscripcionVigente con lista vacia devuelve Optional.empty (Req 3.4)")
    void suscripcionVigenteListaVaciaEsEmpty() {
        assertThat(ServicioEmpresas.suscripcionVigente(List.of())).isEmpty();
    }

    // ------------------------------------------------------------------
    // Tarea 1.3 (Req 4.9): fabrica EmpresaDto con/ sin plan vigente.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("EmpresaDto.de(empresa) deja planVigente en null (contrato de rutas no enriquecidas, Req 4.9)")
    void empresaDtoSinPlanVigenteEsNull() {
        Empresa empresa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");

        EmpresaDto dto = EmpresaDto.de(empresa);

        assertThat(dto.id()).isEqualTo(empresa.getId());
        assertThat(dto.planVigente()).isNull();
    }

    @Test
    @DisplayName("EmpresaDto.de(empresa, planVigente) conserva el plan vigente recibido (Req 4.2)")
    void empresaDtoConPlanVigente() {
        Empresa empresa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        UUID suscId = UUID.randomUUID();
        EmpresaDto.PlanVigenteDto plan = new EmpresaDto.PlanVigenteDto(
                "Plan Basico", EstadoSuscripcion.ACTIVA, PLAN_ID, suscId,
                LocalDate.of(2025, 3, 10), null,
                TipoInstrumento.PLAN, "Plan Basico", null, null, false, false, false);

        EmpresaDto dto = EmpresaDto.de(empresa, plan);

        assertThat(dto.id()).isEqualTo(empresa.getId());
        assertThat(dto.planVigente()).isSameAs(plan);
        assertThat(dto.planVigente().nombrePlan()).isEqualTo("Plan Basico");
        assertThat(dto.planVigente().estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(dto.planVigente().planId()).isEqualTo(PLAN_ID);
        assertThat(dto.planVigente().suscripcionId()).isEqualTo(suscId);
    }

    // ------------------------------------------------------------------
    // Tarea 3.5 (Req 4.2, 4.3, 4.8, 4.11): enriquecimiento del plan vigente
    // en consultarEmpresa y listarEmpresasDto.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("consultarEmpresa enriquece planVigente con nombre/estado/ids y fija el tenant (Req 4.2, 4.11)")
    void consultarEmpresaEnriquecePlanVigente() {
        Empresa empresa = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        UUID tenant = empresa.getId();
        Suscripcion activa = suscripcion(tenant, PLAN_ID, EstadoSuscripcion.ACTIVA,
                LocalDate.of(2025, 3, 10), null, T0);
        when(empresaRepository.findById(tenant)).thenReturn(Optional.of(empresa));
        when(suscripcionRepository.findByTenantIdOrderByIdAsc(tenant))
                .thenReturn(List.of(activa));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(planFalso(PLAN_ID)));

        EmpresaDto dto = servicio.consultarEmpresa(tenant);

        assertThat(dto.planVigente()).isNotNull();
        assertThat(dto.planVigente().nombrePlan()).isEqualTo("Plan Basico");
        assertThat(dto.planVigente().estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(dto.planVigente().planId()).isEqualTo(PLAN_ID);
        assertThat(dto.planVigente().suscripcionId()).isEqualTo(activa.getId());
        // La lectura de suscripciones es tenant-scoped por RLS: se fija el tenant.
        verify(tenantSession).applyTenant(tenant);
    }

    @Test
    @DisplayName("consultarEmpresa sin suscripciones deja planVigente en null (Req 4.3)")
    void consultarEmpresaSinSuscripcionesPlanVigenteNull() {
        Empresa empresa = Empresa.crear("Anuncios del Sur", "ASU120101AB1", GIRO_ID, "super");
        UUID tenant = empresa.getId();
        when(empresaRepository.findById(tenant)).thenReturn(Optional.of(empresa));
        when(suscripcionRepository.findByTenantIdOrderByIdAsc(tenant)).thenReturn(List.of());

        EmpresaDto dto = servicio.consultarEmpresa(tenant);

        assertThat(dto.planVigente()).isNull();
        verify(tenantSession).applyTenant(tenant);
    }

    @Test
    @DisplayName("listarEmpresasDto enriquece por lote: primera con plan, segunda sin plan; nombres via findAllById (Req 4.8, 4.11)")
    void listarEmpresasDtoEnriquecePorLote() {
        Pageable pageable = PageRequest.of(0, 20);
        Empresa conPlan = Empresa.crear("Anuncios del Norte", "ANO120101AB1", GIRO_ID, "super");
        Empresa sinPlan = Empresa.crear("Anuncios del Sur", "ASU120101AB1", GIRO_ID, "super");
        Suscripcion activa = suscripcion(conPlan.getId(), PLAN_ID, EstadoSuscripcion.ACTIVA,
                LocalDate.of(2025, 3, 10), null, T0);

        Page<Empresa> pagina = new PageImpl<>(List.of(conPlan, sinPlan), pageable, 2);
        when(empresaRepository.findAll(pageable)).thenReturn(pagina);
        when(suscripcionRepository.findByTenantIdOrderByIdAsc(conPlan.getId()))
                .thenReturn(List.of(activa));
        when(suscripcionRepository.findByTenantIdOrderByIdAsc(sinPlan.getId()))
                .thenReturn(List.of());
        when(planRepository.findAllById(any())).thenReturn(List.of(planFalso(PLAN_ID)));

        Page<EmpresaDto> resultado = servicio.listarEmpresasDto(null, null, pageable);

        List<EmpresaDto> filas = resultado.getContent();
        assertThat(filas).hasSize(2);
        // Primera Empresa: plan vigente enriquecido con el nombre resuelto.
        assertThat(filas.get(0).id()).isEqualTo(conPlan.getId());
        assertThat(filas.get(0).planVigente()).isNotNull();
        assertThat(filas.get(0).planVigente().nombrePlan()).isEqualTo("Plan Basico");
        assertThat(filas.get(0).planVigente().estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(filas.get(0).planVigente().suscripcionId()).isEqualTo(activa.getId());
        // Segunda Empresa: sin suscripcion => sin plan vigente.
        assertThat(filas.get(1).id()).isEqualTo(sinPlan.getId());
        assertThat(filas.get(1).planVigente()).isNull();

        // Anti-N+1 (Req 4.8): los nombres se resuelven por LOTE con findAllById, no
        // fila por fila con findById.
        verify(planRepository).findAllById(any());
        verify(planRepository, never()).findById(any());
    }

    // ------------------------------------------------------------------
    // Fabricas por reflexion (entidades JPA con constructor protegido).
    // ------------------------------------------------------------------

    /**
     * Fabrica una {@link Suscripcion} con estado, vigencias y {@code createdAt}
     * controlados para las pruebas. Parte de la factoria de dominio
     * {@link Suscripcion#crear} (que la deja ACTIVA), aplica la transicion de
     * estado deseada y fija {@code createdAt} por reflexion (solo se asigna en el
     * ciclo de persistencia real, ausente en estas pruebas unitarias).
     */
    private static Suscripcion suscripcion(UUID tenantId, UUID planId, EstadoSuscripcion estado,
                                           LocalDate vigenciaInicio, LocalDate vigenciaFin,
                                           Instant createdAt) {
        Suscripcion s = Suscripcion.crear(tenantId, planId, vigenciaInicio, vigenciaFin, "super");
        switch (estado) {
            case ACTIVA -> { /* ya viene activa de la factoria */ }
            case SUSPENDIDA -> s.suspender("super");
            case CANCELADA -> s.cancelar("super");
        }
        set(s, "createdAt", createdAt);
        return s;
    }

    /** Giro ACTIVO con id fijo (Req 2.2): usa la fabrica de dominio y fija su id. */
    private static Giro giroActivo(UUID id) {
        return giroConClave(id, "anuncios-luminosos");
    }

    /** Giro ACTIVO con id y clave fijos (Req 3.1): fabrica de dominio + id. */
    private static Giro giroConClave(UUID id, String clave) {
        Giro giro = Giro.crear(clave, clave, null, "super");
        set(giro, "id", id);
        return giro;
    }

    private static Plan planFalso(UUID id) {
        Plan plan = instanciar(Plan.class);
        set(plan, "id", id);
        set(plan, "nombre", "Plan Basico");
        set(plan, "maxUsuarios", 10);
        return plan;
    }

    private static Rol rolFalso(UUID id, String nombre, UUID tenantId) {
        Rol rol = instanciar(Rol.class);
        set(rol, "id", id);
        set(rol, "nombre", nombre);
        set(rol, "tenantId", tenantId);
        set(rol, "predefinido", true);
        return rol;
    }

    private static <T> T instanciar(Class<T> tipo) {
        try {
            var ctor = tipo.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo instanciar " + tipo.getSimpleName(), e);
        }
    }

    private static void set(Object destino, String campo, Object valor) {
        try {
            Field f = destino.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(destino, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo asignar el campo '" + campo + "'", e);
        }
    }
}
