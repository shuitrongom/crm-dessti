package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.roles.Rol;
import com.dessti.crm.platform.security.roles.RolRepository;
import com.dessti.crm.platform.security.sesiones.RegistroSesionesPort;
import com.dessti.crm.platform.security.usuarios.CrearUsuarioCommand;
import com.dessti.crm.platform.security.usuarios.LimiteUsuariosPort;
import com.dessti.crm.platform.security.usuarios.ServicioUsuarios;
import com.dessti.crm.platform.security.usuarios.Usuario;
import com.dessti.crm.platform.security.usuarios.UsuarioRepository;
import com.dessti.crm.platform.tenant.TenantContext;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterTry;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 28: Limite de
 * usuarios del Plan</strong> (Req 25.3).
 *
 * <p><strong>Enfoque de modelado.</strong> La invariante nuclear de la Property
 * 28 es aritmetica: dado un Plan con {@code max_usuarios = N} y un numero de
 * cuentas activas actuales, la creacion del Usuario N+1 dentro de la Empresa se
 * rechaza informando el limite alcanzado. Esa aritmetica vive integramente en
 * {@link LimiteUsuariosPlanAdapter#puedeCrearUsuario(UUID)}, cuya unica
 * dependencia observable es <em>(a)</em> resolver la Suscripcion activa y su
 * Plan y <em>(b)</em> contar las cuentas activas de la Empresa. Por ello el
 * grueso de la property (metodos {@code limite*}) ejercita el <strong>adaptador
 * REAL</strong> {@code LimiteUsuariosPlanAdapter} con <em>fakes en memoria</em>
 * de sus tres repositorios (Suscripcion/Plan/Usuario), mirroreando linea por
 * linea la semantica de produccion: se autoriza <strong>solo si</strong>
 * {@code activos < max_usuarios} (estricto), {@code max_usuarios = 0} bloquea
 * siempre, y sin Suscripcion activa o sin Plan se deniega por defecto. Este es
 * el enfoque (A): comprueba el limite en si mismo sin base de datos ni contexto
 * de Spring, sobre la pieza de produccion.</p>
 *
 * <p>Adicionalmente, dos comprobaciones de <strong>cableado</strong> (enfoque
 * B, metodos {@code cableado*}) ejercitan
 * {@link ServicioUsuarios#crearUsuario(CrearUsuarioCommand)} con un
 * {@link LimiteUsuariosPort} <em>mockeado</em> que devuelve un booleano
 * controlado, para verificar que el gating esta correctamente enganchado:
 * cuando el puerto deniega, el servicio lanza {@link ReglaNegocioException}
 * (HTTP 422) informando el limite y <em>no persiste</em> ninguna cuenta; cuando
 * el puerto autoriza y el resto de validaciones pasan, la cuenta se crea.</p>
 *
 * <p>No se emplean mocks para la aritmetica del limite (enfoque A usa fakes
 * deterministas de los repositorios); los mocks del enfoque B solo aislan a
 * {@code ServicioUsuarios} de sus colaboradores para observar el gating, sin
 * falsear la logica bajo prueba.</p>
 */
class LimiteUsuariosPlanPropertyTest {

    // ----------------------------------------------------------------------
    // Enfoque A — Adaptador REAL sobre fakes en memoria de sus repositorios
    // ----------------------------------------------------------------------

    /**
     * Construye el adaptador REAL {@link LimiteUsuariosPlanAdapter} respaldado por
     * fakes en memoria que reproducen exactamente las tres consultas de las que
     * depende el adaptador: la Suscripcion activa de la Empresa, el Plan de esa
     * Suscripcion y el conteo de cuentas activas de la Empresa.
     *
     * @param tenantId  Empresa evaluada.
     * @param hayActiva si existe una Suscripcion en estado ACTIVA para la Empresa.
     * @param hayPlan   si el Plan referido por la Suscripcion activa existe.
     * @param maxUsuarios {@code max_usuarios} del Plan (solo relevante si hayPlan).
     * @param activos   numero de cuentas activas actuales de la Empresa.
     */
    private static LimiteUsuariosPlanAdapter adaptadorReal(UUID tenantId, boolean hayActiva,
                                                           boolean hayPlan, int maxUsuarios,
                                                           long activos) {
        UUID planId = UUID.randomUUID();

        SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
        if (hayActiva) {
            Suscripcion activa = Suscripcion.crearBasica(
                    tenantId, planId, java.time.LocalDate.now(), "test");
            when(suscripciones.findFirstByTenantIdAndEstadoOrderByIdAsc(
                    tenantId, EstadoSuscripcion.ACTIVA)).thenReturn(Optional.of(activa));
        } else {
            when(suscripciones.findFirstByTenantIdAndEstadoOrderByIdAsc(
                    tenantId, EstadoSuscripcion.ACTIVA)).thenReturn(Optional.empty());
        }

        PlanRepository planes = mock(PlanRepository.class);
        if (hayPlan) {
            Plan plan = PlanTestFactory.conModulos("Plan-" + maxUsuarios, maxUsuarios, Set.of());
            when(planes.findById(any(UUID.class))).thenReturn(Optional.of(plan));
        } else {
            when(planes.findById(any(UUID.class))).thenReturn(Optional.empty());
        }

        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.countByTenantIdAndActivoTrue(tenantId)).thenReturn(activos);

        return new LimiteUsuariosPlanAdapter(suscripciones, planes, usuarios);
    }

    // Feature: crm-anuncios-luminosos, Property 28: Para cualquier Plan con un máximo de N Usuarios y para cualquier secuencia de altas, la creación del Usuario N+1 dentro de la Empresa se rechaza informando el límite alcanzado.
    @Property(tries = 1000)
    void limiteAutorizaSoloEstrictamentePorDebajoDelMaximo(
            @ForAll("tenants") UUID tenantId,
            @ForAll @IntRange(min = 0, max = 1000) int maxUsuarios,
            @ForAll @IntRange(min = 0, max = 1000) int activos) {

        LimiteUsuariosPlanAdapter adaptador = adaptadorReal(
                tenantId, true, true, maxUsuarios, activos);

        boolean permitido = adaptador.puedeCrearUsuario(tenantId);

        // Invariante 1: se autoriza si y solo si activos < max (estricto).
        assertThat(permitido)
                .as("con Suscripcion y Plan, se autoriza sii activos(%d) < max(%d)",
                        activos, maxUsuarios)
                .isEqualTo(activos < maxUsuarios);

        // En la frontera (activos == max) y por encima (> max) se deniega.
        if (activos >= maxUsuarios) {
            assertThat(permitido)
                    .as("en la frontera o por encima del maximo se deniega")
                    .isFalse();
        }
    }

    // Feature: crm-anuncios-luminosos, Property 28: Para cualquier Plan con un máximo de N Usuarios y para cualquier secuencia de altas, la creación del Usuario N+1 dentro de la Empresa se rechaza informando el límite alcanzado.
    @Property(tries = 1000)
    void limiteConMaximoCeroSiempreDeniega(
            @ForAll("tenants") UUID tenantId,
            @ForAll @IntRange(min = 0, max = 1000) int activos) {

        LimiteUsuariosPlanAdapter adaptador = adaptadorReal(
                tenantId, true, true, 0, activos);

        // Invariante 2: max_usuarios == 0 bloquea toda creacion sin importar el conteo.
        assertThat(adaptador.puedeCrearUsuario(tenantId))
                .as("un Plan con max_usuarios = 0 deniega siempre (activos=%d)", activos)
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 28: Para cualquier Plan con un máximo de N Usuarios y para cualquier secuencia de altas, la creación del Usuario N+1 dentro de la Empresa se rechaza informando el límite alcanzado.
    @Property(tries = 1000)
    void limiteSinSuscripcionOSinPlanDeniegaPorDefecto(
            @ForAll("tenants") UUID tenantId,
            @ForAll @IntRange(min = 0, max = 1000) int maxUsuarios,
            @ForAll @IntRange(min = 0, max = 1000) int activos,
            @ForAll boolean falta) {

        // falta==true: no hay Suscripcion activa. falta==false: hay Suscripcion
        // activa pero su Plan no existe. En ambos casos: deny by default.
        boolean hayActiva = !falta;
        boolean hayPlan = falta; // irrelevante cuando no hay Suscripcion activa
        LimiteUsuariosPlanAdapter adaptador = adaptadorReal(
                tenantId, hayActiva, hayPlan, maxUsuarios, activos);

        // Invariante 3: sin Suscripcion activa o sin Plan vigente se deniega.
        assertThat(adaptador.puedeCrearUsuario(tenantId))
                .as("sin Suscripcion activa o sin Plan, se deniega por defecto")
                .isFalse();

        // Y tenant nulo tambien se deniega (guarda del adaptador).
        assertThat(adaptador.puedeCrearUsuario(null))
                .as("un tenant nulo se deniega")
                .isFalse();
    }

    // ----------------------------------------------------------------------
    // Enfoque B — Cableado del gating en ServicioUsuarios.crearUsuario
    // ----------------------------------------------------------------------

    private UsuarioRepository usuarioRepository;
    private LimiteUsuariosPort limiteUsuarios;
    private ServicioUsuarios servicioUsuarios;

    private ServicioUsuarios servicioConLimite(boolean permitir, UUID tenantId, UUID rolId) {
        usuarioRepository = mock(UsuarioRepository.class);
        RolRepository rolRepository = mock(RolRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        RegistroSesionesPort registroSesiones = mock(RegistroSesionesPort.class);
        limiteUsuarios = mock(LimiteUsuariosPort.class);
        var modulosHabilitados =
                mock(com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort.class);

        // Rol asignable de la propia Empresa (tenant == actual): pasa la validacion previa.
        // Al ser un rol PERSONALIZADO (tenant no NULL), el gating por modulo no aplica.
        Rol rol = Rol.personalizado(tenantId, "ventas", Set.of(), "test");
        when(rolRepository.findById(rolId)).thenReturn(Optional.of(rol));

        when(limiteUsuarios.puedeCrearUsuario(tenantId)).thenReturn(permitir);
        when(usuarioRepository.existsByIdentificadorAcceso(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        return new ServicioUsuarios(usuarioRepository, rolRepository, passwordEncoder,
                auditoria, registroSesiones, limiteUsuarios, modulosHabilitados);
    }

    @AfterTry
    void limpiarTenant() {
        TenantContext.clear();
    }

    // Feature: crm-anuncios-luminosos, Property 28: Para cualquier Plan con un máximo de N Usuarios y para cualquier secuencia de altas, la creación del Usuario N+1 dentro de la Empresa se rechaza informando el límite alcanzado.
    @Property(tries = 1000)
    void cableadoRechazaYNoPersisteCuandoElPuertoDeniega(
            @ForAll("tenants") UUID tenantId,
            @ForAll("identificadores") String identificador) {

        UUID rolId = UUID.randomUUID();
        servicioUsuarios = servicioConLimite(false, tenantId, rolId);
        TenantContext.set(tenantId);

        CrearUsuarioCommand comando = new CrearUsuarioCommand(
                identificador, "secreta-123", null, Set.of(rolId));

        // Cuando el puerto deniega -> ReglaNegocioException (422) informando el limite.
        assertThatThrownBy(() -> servicioUsuarios.crearUsuario(comando))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("limite de Usuarios del Plan");

        // Y NO se persiste ninguna cuenta.
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    // Feature: crm-anuncios-luminosos, Property 28: Para cualquier Plan con un máximo de N Usuarios y para cualquier secuencia de altas, la creación del Usuario N+1 dentro de la Empresa se rechaza informando el límite alcanzado.
    @Property(tries = 1000)
    void cableadoCreaCuandoElPuertoAutoriza(
            @ForAll("tenants") UUID tenantId,
            @ForAll("identificadores") String identificador) {

        UUID rolId = UUID.randomUUID();
        servicioUsuarios = servicioConLimite(true, tenantId, rolId);
        TenantContext.set(tenantId);

        CrearUsuarioCommand comando = new CrearUsuarioCommand(
                identificador, "secreta-123", null, Set.of(rolId));

        // Cuando el puerto autoriza y el resto de validaciones pasan, se crea la cuenta.
        var dto = servicioUsuarios.crearUsuario(comando);
        assertThat(dto).as("con el puerto autorizando, la creacion devuelve un DTO").isNotNull();

        // La cuenta se persistio exactamente una vez.
        verify(usuarioRepository).save(any(Usuario.class));
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> tenants() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> identificadores() {
        // Identificadores de acceso no vacios (ASCII), estables para mensajes.
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(24)
                .withCharRange('a', 'z')
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '-');
    }
}
