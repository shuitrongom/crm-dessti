package com.dessti.crm.platform.empresas.offboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.empresas.EmpresaRepository;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas unitarias de {@link ServicioOffboarding} (Req 69, tarea 14.4). Usan
 * dobles de Mockito y un {@link Clock} fijo; no arrancan contexto de Spring ni
 * base de datos.
 *
 * <p>Cubren: la exportacion ejecuta los exportadores registrados, refuerza el
 * ambito RLS del tenant objetivo y audita (Req 69.1, 69.5, 69.6); la eliminacion
 * se rechaza (422) antes de expirar la gracia o si la Empresa no esta cancelada
 * (Req 69.3); tras la gracia la eliminacion corre los borradores, preserva los
 * comprobantes fiscales y audita el alcance (Req 69.3, 69.4, 69.6); la
 * cancelacion fija fecha_cancelacion + fin_periodo_gracia desde el Periodo_Gracia
 * configurado (Req 69.2); el admin_empresa solo puede exportar su propio tenant
 * (Req 69.1, 23.3); y el registro vacio funciona (marco extensible).</p>
 */
class ServicioOffboardingTest {

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");
    private static final Duration GRACIA = Duration.ofDays(30);

    private EmpresaRepository empresaRepository;
    private TenantSessionInitializer tenantSession;
    private AuditoriaPort auditoria;
    private Autorizador autorizador;
    private Clock clock;

    @BeforeEach
    void setUp() {
        empresaRepository = mock(EmpresaRepository.class);
        tenantSession = mock(TenantSessionInitializer.class);
        auditoria = mock(AuditoriaPort.class);
        autorizador = mock(Autorizador.class);
        clock = Clock.fixed(T0, ZoneOffset.UTC);
    }

    @AfterEach
    void limpiarContexto() {
        TenantContext.clear();
    }

    private ServicioOffboarding servicioCon(List<RecursoTenantOffboarding> recursos) {
        return new ServicioOffboarding(
                empresaRepository, recursos, tenantSession, auditoria, autorizador,
                new OffboardingProperties(GRACIA), clock);
    }

    /** Exportador/borrador de prueba: cuenta las invocaciones y simula conteos. */
    private static final class RecursoFake implements RecursoTenantOffboarding {
        private final String nombre;
        private final boolean fiscal;
        private final long aEliminar;
        UUID exportadoPara;
        UUID eliminadoPara;

        RecursoFake(String nombre, boolean fiscal, long aEliminar) {
            this.nombre = nombre;
            this.fiscal = fiscal;
            this.aEliminar = aEliminar;
        }

        @Override public String nombreRecurso() { return nombre; }
        @Override public boolean esComprobanteFiscal() { return fiscal; }
        @Override public Object exportar(UUID tenantId) {
            this.exportadoPara = tenantId;
            return Map.of("recurso", nombre, "tenant", tenantId.toString());
        }
        @Override public long eliminarOAnonimizar(UUID tenantId) {
            this.eliminadoPara = tenantId;
            return aEliminar;
        }
    }

    private Empresa empresaActiva() {
        return Empresa.crear("Rotulos SA", "RSA010101AAA", java.util.UUID.randomUUID(), "super-admin");
    }

    private Empresa empresaCancelada(Instant cancelacion, Duration gracia) {
        Empresa e = empresaActiva();
        e.cancelar(cancelacion, gracia, "super-admin");
        return e;
    }

    // ------------------------------------------------------------------
    // Exportacion (Req 69.1, 69.5, 69.6)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Exportar (super_admin): ejecuta exportadores, fija RLS al tenant objetivo y audita (Req 69.1, 69.5, 69.6)")
    void exportarEjecutaExportadoresYAudita() {
        Empresa empresa = empresaActiva();
        UUID tenant = empresa.getId();
        RecursoFake clientes = new RecursoFake("cliente", false, 0);
        RecursoFake facturas = new RecursoFake("factura", true, 0);
        when(autorizador.tiene("offboarding", "exportar")).thenReturn(true); // super_admin
        when(empresaRepository.existsById(tenant)).thenReturn(true);
        ServicioOffboarding servicio = servicioCon(List.of(clientes, facturas));

        ExportacionTenantDto dto = servicio.exportar(tenant);

        assertThat(dto.tenantId()).isEqualTo(tenant);
        assertThat(dto.generadoEn()).isEqualTo(T0);
        assertThat(dto.recursos()).containsKeys("cliente", "factura");
        assertThat(clientes.exportadoPara).isEqualTo(tenant);
        assertThat(facturas.exportadoPara).isEqualTo(tenant);
        // Refuerzo RLS: se fija app.current_tenant al tenant objetivo (Req 69.5).
        verify(tenantSession).applyTenant(tenant);

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        assertThat(captor.getValue().accion()).isEqualTo("exportar");
        assertThat(captor.getValue().recurso()).isEqualTo("offboarding");
        assertThat(captor.getValue().detalle()).contains(tenant.toString());
    }

    @Test
    @DisplayName("Exportar: Empresa inexistente -> 404 (Req 24.1)")
    void exportarEmpresaInexistente404() {
        UUID tenant = UUID.randomUUID();
        when(autorizador.tiene("offboarding", "exportar")).thenReturn(true);
        when(empresaRepository.existsById(tenant)).thenReturn(false);
        ServicioOffboarding servicio = servicioCon(List.of());

        assertThatThrownBy(() -> servicio.exportar(tenant))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(tenantSession, never()).applyTenant(any());
    }

    @Test
    @DisplayName("Exportar (registro vacio): el marco funciona y devuelve exportacion vacia (marco extensible)")
    void exportarRegistroVacio() {
        Empresa empresa = empresaActiva();
        UUID tenant = empresa.getId();
        when(autorizador.tiene("offboarding", "exportar")).thenReturn(true);
        when(empresaRepository.existsById(tenant)).thenReturn(true);
        ServicioOffboarding servicio = servicioCon(List.of());

        ExportacionTenantDto dto = servicio.exportar(tenant);

        assertThat(dto.recursos()).isEmpty();
        verify(tenantSession).applyTenant(tenant);
        verify(auditoria).registrar(any());
    }

    @Test
    @DisplayName("Exportar (admin_empresa): solo puede exportar SU tenant; otro tenant -> 404 (Req 69.1, 23.3)")
    void adminEmpresaSoloExportaSuTenant() {
        UUID propio = UUID.randomUUID();
        UUID ajeno = UUID.randomUUID();
        // No es super_admin: carece del permiso de plataforma.
        when(autorizador.tiene("offboarding", "exportar")).thenReturn(false);
        TenantContext.set(propio);
        ServicioOffboarding servicio = servicioCon(List.of());

        // Intento de exportar OTRO tenant: 404 sin revelar existencia (Req 23.3).
        assertThatThrownBy(() -> servicio.exportar(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(tenantSession, never()).applyTenant(any());
    }

    @Test
    @DisplayName("Exportar (admin_empresa): puede exportar su propio tenant (Req 69.1)")
    void adminEmpresaExportaSuPropioTenant() {
        Empresa empresa = empresaActiva();
        UUID propio = empresa.getId();
        when(autorizador.tiene("offboarding", "exportar")).thenReturn(false);
        when(empresaRepository.existsById(propio)).thenReturn(true);
        TenantContext.set(propio);
        ServicioOffboarding servicio = servicioCon(List.of());

        ExportacionTenantDto dto = servicio.exportar(propio);

        assertThat(dto.tenantId()).isEqualTo(propio);
        verify(tenantSession).applyTenant(propio);
    }

    // ------------------------------------------------------------------
    // Cancelacion con Periodo_Gracia (Req 69.2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Cancelar: fija estado CANCELADA, fecha_cancelacion y fin_periodo_gracia desde el Periodo_Gracia configurado (Req 69.2)")
    void cancelarFijaGracia() {
        Empresa empresa = empresaActiva();
        UUID tenant = empresa.getId();
        when(empresaRepository.findById(tenant)).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));
        ServicioOffboarding servicio = servicioCon(List.of());

        Empresa resultado = servicio.cancelarEIniciarGracia(tenant);

        assertThat(resultado.estaCancelada()).isTrue();
        assertThat(resultado.getFechaCancelacion()).isEqualTo(T0);
        assertThat(resultado.getFinPeriodoGracia()).isEqualTo(T0.plus(GRACIA));
        verify(auditoria).registrar(any());
    }

    // ------------------------------------------------------------------
    // Eliminacion definitiva (Req 69.3, 69.4, 69.6)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Eliminar: rechazada (422) si la Empresa no esta cancelada (Req 69.3)")
    void eliminarRechazaSiNoCancelada() {
        Empresa empresa = empresaActiva();
        UUID tenant = empresa.getId();
        when(empresaRepository.findById(tenant)).thenReturn(Optional.of(empresa));
        ServicioOffboarding servicio = servicioCon(List.of());

        assertThatThrownBy(() -> servicio.eliminarDefinitivamente(tenant))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no esta cancelada");
        verify(tenantSession, never()).applyTenant(any());
    }

    @Test
    @DisplayName("Eliminar: rechazada (422) si el Periodo_Gracia aun no ha expirado (Req 69.3)")
    void eliminarRechazaSiGraciaVigente() {
        // Cancelada en T0 con 30 dias de gracia; se elimina en T0 (gracia vigente).
        Empresa empresa = empresaCancelada(T0, GRACIA);
        UUID tenant = empresa.getId();
        when(empresaRepository.findById(tenant)).thenReturn(Optional.of(empresa));
        ServicioOffboarding servicio = servicioCon(List.of());

        assertThatThrownBy(() -> servicio.eliminarDefinitivamente(tenant))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Periodo_Gracia");
        verify(tenantSession, never()).applyTenant(any());
    }

    @Test
    @DisplayName("Eliminar (tras la gracia): corre borradores, preserva fiscales, fija RLS y audita el alcance (Req 69.3, 69.4, 69.5, 69.6)")
    void eliminarTrasGraciaCorreBorradoresYPreservaFiscales() {
        // Cancelada en el pasado, gracia ya expirada respecto a T0.
        Empresa empresa = empresaCancelada(T0.minus(Duration.ofDays(31)), GRACIA);
        UUID tenant = empresa.getId();
        when(empresaRepository.findById(tenant)).thenReturn(Optional.of(empresa));
        RecursoFake clientes = new RecursoFake("cliente", false, 5);
        RecursoFake facturas = new RecursoFake("factura", true, 3); // fiscal: se preserva
        ServicioOffboarding servicio = servicioCon(List.of(clientes, facturas));

        ResultadoEliminacionTenantDto dto = servicio.eliminarDefinitivamente(tenant);

        // Solo el recurso NO fiscal se elimino, acotado al tenant objetivo.
        assertThat(dto.eliminadosPorRecurso()).containsEntry("cliente", 5L);
        assertThat(dto.eliminadosPorRecurso()).doesNotContainKey("factura");
        assertThat(clientes.eliminadoPara).isEqualTo(tenant);
        // El comprobante fiscal se preserva: nunca se invoca su borrado (Req 69.4).
        assertThat(facturas.eliminadoPara).isNull();
        assertThat(dto.preservadosFiscales()).containsExactly("factura");
        // Refuerzo RLS al tenant objetivo (Req 69.5).
        verify(tenantSession).applyTenant(tenant);

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        assertThat(captor.getValue().accion()).isEqualTo("eliminar");
        assertThat(captor.getValue().detalle())
                .contains(tenant.toString())
                .contains("preservados_fiscales=[factura]");
    }

    @Test
    @DisplayName("Eliminar: Empresa inexistente -> 404")
    void eliminarEmpresaInexistente404() {
        UUID tenant = UUID.randomUUID();
        when(empresaRepository.findById(tenant)).thenReturn(Optional.empty());
        ServicioOffboarding servicio = servicioCon(List.of());

        assertThatThrownBy(() -> servicio.eliminarDefinitivamente(tenant))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("tenantId nulo: fail-closed en todas las operaciones (seguridad)")
    void tenantNuloFailClosed() {
        ServicioOffboarding servicio = servicioCon(List.of());
        assertThatThrownBy(() -> servicio.exportar(null)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.cancelarEIniciarGracia(null)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.eliminarDefinitivamente(null)).isInstanceOf(ReglaNegocioException.class);
        verify(tenantSession, never()).applyTenant(any());
    }
}
