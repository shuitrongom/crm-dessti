package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas unitarias de {@link ServicioSuscripciones} (Req 25.2, 25.5):
 * <ul>
 *   <li>crearSuscripcion valida Empresa/Plan, persiste en estado activa y audita.</li>
 *   <li>crearSuscripcion da 404 si la Empresa o el Plan no existen.</li>
 *   <li>Transiciones de estado (activar/suspender/cancelar) y su auditoria.</li>
 *   <li>No se puede suspender/activar una Suscripcion cancelada (422).</li>
 *   <li>actualizarVigencia rechaza un fin anterior al inicio (422).</li>
 * </ul>
 *
 * <p>No usa {@code @SpringBootTest} ni Testcontainers.</p>
 */
class ServicioSuscripcionesTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private SuscripcionRepository suscripcionRepository;
    private EmpresaRepository empresaRepository;
    private PlanRepository planRepository;
    private PaqueteSuscripcionRepository paqueteSuscripcionRepository;
    private AuditoriaPort auditoria;
    private TenantSessionInitializer tenantSession;
    private ServicioSuscripciones servicio;

    @BeforeEach
    void preparar() {
        suscripcionRepository = mock(SuscripcionRepository.class);
        empresaRepository = mock(EmpresaRepository.class);
        planRepository = mock(PlanRepository.class);
        paqueteSuscripcionRepository = mock(PaqueteSuscripcionRepository.class);
        auditoria = mock(AuditoriaPort.class);
        tenantSession = mock(TenantSessionInitializer.class);
        servicio = new ServicioSuscripciones(
                suscripcionRepository, empresaRepository, planRepository,
                paqueteSuscripcionRepository, auditoria, tenantSession);
    }

    @Test
    @DisplayName("crearSuscripcion valida Empresa/Plan, persiste activa y audita (Req 25.2, 25.5)")
    void creaSuscripcionValidaYAudita() {
        when(empresaRepository.existsById(TENANT)).thenReturn(true);
        when(planRepository.existsById(PLAN)).thenReturn(true);
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new CrearSuscripcionCommand(TENANT, PLAN, LocalDate.of(2025, 1, 1), null);
        SuscripcionDto dto = servicio.crearSuscripcion(comando);

        assertThat(dto.tenantId()).isEqualTo(TENANT);
        assertThat(dto.planId()).isEqualTo(PLAN);
        assertThat(dto.estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(dto.vigenciaInicio()).isEqualTo(LocalDate.of(2025, 1, 1));

        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.tenantId()).isEmpty(); // se audita como plataforma
        assertThat(evento.recurso()).isEqualTo(ServicioSuscripciones.RECURSO_SUSCRIPCION);
        assertThat(evento.accion()).isEqualTo("crear");
    }

    @Test
    @DisplayName("crearSuscripcion da 404 si la Empresa no existe (Req 25.2)")
    void creaSuscripcionEmpresaInexistente() {
        when(empresaRepository.existsById(TENANT)).thenReturn(false);

        var comando = new CrearSuscripcionCommand(TENANT, PLAN, null, null);

        assertThatThrownBy(() -> servicio.crearSuscripcion(comando))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("crearSuscripcion da 404 si el Plan no existe (Req 25.2)")
    void creaSuscripcionPlanInexistente() {
        when(empresaRepository.existsById(TENANT)).thenReturn(true);
        when(planRepository.existsById(PLAN)).thenReturn(false);

        var comando = new CrearSuscripcionCommand(TENANT, PLAN, null, null);

        assertThatThrownBy(() -> servicio.crearSuscripcion(comando))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("crearSuscripcion rechaza una vigencia con fin anterior al inicio -> 422 (Req 25.2)")
    void creaSuscripcionVigenciaInvalida() {
        when(empresaRepository.existsById(TENANT)).thenReturn(true);
        when(planRepository.existsById(PLAN)).thenReturn(true);

        var comando = new CrearSuscripcionCommand(
                TENANT, PLAN, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 1, 1));

        assertThatThrownBy(() -> servicio.crearSuscripcion(comando))
                .isInstanceOf(ReglaNegocioException.class);
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("suspender cambia el estado a suspendida y audita (Req 25.2, 25.5)")
    void suspendeSuscripcion() {
        UUID id = UUID.randomUUID();
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN, LocalDate.of(2025, 1, 1), null, "super");
        when(suscripcionRepository.findById(id)).thenReturn(Optional.of(susc));
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SuscripcionDto dto = servicio.suspender(id);

        assertThat(dto.estado()).isEqualTo(EstadoSuscripcion.SUSPENDIDA);
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo("suspender");
    }

    @Test
    @DisplayName("cancelar es final: no se puede reactivar una Suscripcion cancelada -> 422 (Req 25.2)")
    void noSePuedeActivarCancelada() {
        UUID id = UUID.randomUUID();
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN, LocalDate.of(2025, 1, 1), null, "super");
        susc.cancelar("super");
        when(suscripcionRepository.findById(id)).thenReturn(Optional.of(susc));

        assertThatThrownBy(() -> servicio.activar(id))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("actualizarVigencia rechaza un fin anterior al inicio -> 422 (Req 25.2)")
    void actualizarVigenciaInvalida() {
        UUID id = UUID.randomUUID();
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN, LocalDate.of(2025, 1, 1), null, "super");
        when(suscripcionRepository.findById(id)).thenReturn(Optional.of(susc));

        assertThatThrownBy(() -> servicio.actualizarVigencia(
                id, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 4, 1)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("fijarMonedaFacturacion fija app.current_tenant ANTES de leer la suscripcion (RLS)")
    void fijarMonedaFijaTenantAntesDeLeer() {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN, LocalDate.of(2025, 1, 1), null, "super");
        when(suscripcionRepository.findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(susc));
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.fijarMonedaFacturacion(TENANT, "USD");

        // El super_admin no tiene tenant en contexto; se debe fijar el tenant en la
        // transaccion ANTES de consultar `suscripcion` (protegida por RLS), o la
        // Suscripcion activa existente quedaria oculta (falso 404).
        InOrder orden = inOrder(tenantSession, suscripcionRepository);
        orden.verify(tenantSession).applyTenant(TENANT);
        orden.verify(suscripcionRepository)
                .findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA);
    }

    @Test
    @DisplayName("actualizarModulosEmpresa fija app.current_tenant ANTES de leer la suscripcion (RLS)")
    void actualizarModulosFijaTenantAntesDeLeer() {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN, LocalDate.of(2025, 1, 1), null, "super");
        when(suscripcionRepository.findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(susc));
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.actualizarModulosEmpresa(TENANT, null);

        InOrder orden = inOrder(tenantSession, suscripcionRepository);
        orden.verify(tenantSession).applyTenant(TENANT);
        orden.verify(suscripcionRepository)
                .findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA);
    }

    @Test
    @DisplayName("listarPorEmpresa fija app.current_tenant ANTES de listar (RLS)")
    void listarPorEmpresaFijaTenantAntesDeListar() {
        when(suscripcionRepository.findByTenantIdOrderByIdAsc(TENANT)).thenReturn(List.of());

        servicio.listarPorEmpresa(TENANT);

        InOrder orden = inOrder(tenantSession, suscripcionRepository);
        orden.verify(tenantSession).applyTenant(TENANT);
        orden.verify(suscripcionRepository).findByTenantIdOrderByIdAsc(TENANT);
    }
}
