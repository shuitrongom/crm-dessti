package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas unitarias del adaptador REAL de gating de modulos por Plan
 * {@link PlanModulosPlanAdapter} (Req 25.4):
 * <ul>
 *   <li>Devuelve {@code true} solo para modulos incluidos en el Plan de la
 *       Suscripcion activa (comparacion insensible a mayusculas/espacios).</li>
 *   <li>Devuelve {@code false} para un modulo no habilitado.</li>
 *   <li>Denegacion por defecto: sin Suscripcion activa o sin Plan -> {@code false}.</li>
 * </ul>
 */
class PlanModulosPlanAdapterTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2025-06-15T00:00:00Z"), ZoneOffset.UTC);

    private SuscripcionRepository suscripcionRepository;
    private PlanRepository planRepository;
    private PaqueteSuscripcionRepository paqueteSuscripcionRepository;
    private TenantSessionInitializer tenantSession;
    private PlanModulosPlanAdapter adapter;

    @BeforeEach
    void preparar() {
        suscripcionRepository = mock(SuscripcionRepository.class);
        planRepository = mock(PlanRepository.class);
        paqueteSuscripcionRepository = mock(PaqueteSuscripcionRepository.class);
        tenantSession = mock(TenantSessionInitializer.class);
        adapter = new PlanModulosPlanAdapter(
                suscripcionRepository, planRepository, paqueteSuscripcionRepository, tenantSession, CLOCK);
    }

    private void conSuscripcionActivaYPlan(Set<String> modulos) {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN_ID, LocalDate.of(2025, 1, 1), null, "super");
        Plan plan = PlanTestFactory.conModulos("Premium", 10, modulos);
        when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(
                TENANT, List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA)))
                .thenReturn(List.of(susc));
        when(planRepository.findById(susc.getPlanId())).thenReturn(Optional.of(plan));
    }

    @Test
    @DisplayName("Habilita un modulo incluido en el Plan de la Suscripcion activa (Req 25.4)")
    void habilitaModuloIncluido() {
        conSuscripcionActivaYPlan(Set.of("comercial", "facturacion"));

        assertThat(adapter.moduloHabilitado(TENANT, "facturacion")).isTrue();
        // Insensible a mayusculas y espacios.
        assertThat(adapter.moduloHabilitado(TENANT, "  Facturacion ")).isTrue();
    }

    @Test
    @DisplayName("Deniega un modulo no incluido en el Plan (Req 25.4)")
    void deniegaModuloNoIncluido() {
        conSuscripcionActivaYPlan(Set.of("comercial"));

        assertThat(adapter.moduloHabilitado(TENANT, "facturacion")).isFalse();
    }

    @Test
    @DisplayName("Deniega por defecto cuando no hay Suscripcion activa (Req 25.4)")
    void deniegaSinSuscripcionActiva() {
        when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(
                TENANT, List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA)))
                .thenReturn(List.of());

        assertThat(adapter.moduloHabilitado(TENANT, "facturacion")).isFalse();
    }

    @Test
    @DisplayName("Deniega por defecto cuando el Plan de la Suscripcion no existe (Req 25.4)")
    void deniegaSinPlan() {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN_ID, LocalDate.of(2025, 1, 1), null, "super");
        when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(
                TENANT, List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA)))
                .thenReturn(List.of(susc));
        when(planRepository.findById(susc.getPlanId())).thenReturn(Optional.empty());

        assertThat(adapter.moduloHabilitado(TENANT, "facturacion")).isFalse();
    }

    @Test
    @DisplayName("Deniega ante entradas nulas o vacias (Req 25.4)")
    void deniegaEntradasInvalidas() {
        assertThat(adapter.moduloHabilitado(null, "facturacion")).isFalse();
        assertThat(adapter.moduloHabilitado(TENANT, null)).isFalse();
        assertThat(adapter.moduloHabilitado(TENANT, "  ")).isFalse();
    }

    @Test
    @DisplayName("Fija el tenant (RLS) ANTES de leer la Suscripcion para que la fila sea visible")
    void fijaTenantAntesDeConsultarSuscripcion() {
        // Hereda los modulos del Plan (sin override). Con RLS en `suscripcion`,
        // si no se fijara app.current_tenant la fila quedaria oculta y saldria [].
        conSuscripcionActivaYPlan(Set.of("estrategia"));

        assertThat(adapter.modulosHabilitadosDe(TENANT)).containsExactly("estrategia");

        // El tenant debe fijarse ANTES de la consulta a la Suscripcion, en la
        // misma transaccion, para que la RLS exponga la fila propia del tenant.
        InOrder orden = inOrder(tenantSession, suscripcionRepository);
        orden.verify(tenantSession).applyTenant(TENANT);
        orden.verify(suscripcionRepository)
                .findByTenantIdAndEstadoInOrderByIdAsc(
                        TENANT, List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA));
    }

    @Test
    @DisplayName("NO fija el tenant para el super_admin (tenantId nulo): contexto de plataforma")
    void noFijaTenantParaSuperAdmin() {
        assertThat(adapter.modulosHabilitadosDe(null)).isEmpty();

        // El super_admin opera en ambito de plataforma; jamas debe fijarse un
        // tenant a partir de un id nulo (applyTenant rechazaria el nulo).
        verify(tenantSession, never()).applyTenant(any());
    }
}
