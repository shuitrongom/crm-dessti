package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Pruebas del adaptador de gating {@link PlanModulosPlanAdapter} cuando la
 * Suscripcion define un OVERRIDE por Empresa del subconjunto de modulos
 * (Req 25.4). Complementan a {@link PlanModulosPlanAdapterTest} (que cubre el
 * caso sin override, heredando del Plan).
 *
 * <p>Reglas verificadas:</p>
 * <ul>
 *   <li>Con override presente, manda el override; el Plan NO se consulta.</li>
 *   <li>Override que incluye el modulo =&gt; habilitado.</li>
 *   <li>Override que excluye el modulo =&gt; denegado (aunque el Plan lo incluya).</li>
 *   <li>Override vacio =&gt; todos los modulos denegados.</li>
 *   <li>Sin override =&gt; se recurre al Plan (comportamiento historico).</li>
 * </ul>
 */
class PlanModulosOverrideAdapterTest {

    private static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2025-06-15T00:00:00Z"), ZoneOffset.UTC);

    private SuscripcionRepository suscripcionRepository;
    private PlanRepository planRepository;
    private PaqueteSuscripcionRepository paqueteSuscripcionRepository;
    private com.dessti.crm.platform.tenant.TenantSessionInitializer tenantSession;
    private PlanModulosPlanAdapter adapter;

    @BeforeEach
    void preparar() {
        suscripcionRepository = mock(SuscripcionRepository.class);
        planRepository = mock(PlanRepository.class);
        paqueteSuscripcionRepository = mock(PaqueteSuscripcionRepository.class);
        tenantSession = mock(com.dessti.crm.platform.tenant.TenantSessionInitializer.class);
        adapter = new PlanModulosPlanAdapter(
                suscripcionRepository, planRepository, paqueteSuscripcionRepository, tenantSession, CLOCK);
    }

    private Suscripcion suscripcionActiva() {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN_ID, LocalDate.of(2025, 1, 1), null, "super");
        when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(
                TENANT, List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA)))
                .thenReturn(List.of(susc));
        return susc;
    }

    @Test
    @DisplayName("Override que incluye el modulo lo habilita y NO consulta el Plan (Req 25.4)")
    void overrideIncluyeHabilitaSinConsultarPlan() {
        Suscripcion susc = suscripcionActiva();
        susc.asignarModulos(Set.of("comercial", "facturacion"), "super");

        assertThat(adapter.moduloHabilitado(TENANT, "facturacion")).isTrue();
        assertThat(adapter.moduloHabilitado(TENANT, "  Comercial ")).isTrue(); // insensible a caso/espacios
        // Con override presente, el Plan no se carga.
        verify(planRepository, never()).findById(PLAN_ID);
    }

    @Test
    @DisplayName("Override que excluye el modulo lo deniega aunque el Plan lo incluyera (Req 25.4)")
    void overrideExcluyeDeniega() {
        Suscripcion susc = suscripcionActiva();
        susc.asignarModulos(Set.of("comercial"), "super"); // solo comercial

        assertThat(adapter.moduloHabilitado(TENANT, "facturacion")).isFalse();
        verify(planRepository, never()).findById(PLAN_ID);
    }

    @Test
    @DisplayName("Override vacio deniega todos los modulos (cero habilitados) (Req 25.4)")
    void overrideVacioDeniegaTodo() {
        Suscripcion susc = suscripcionActiva();
        susc.asignarModulos(Set.of(), "super");

        assertThat(adapter.moduloHabilitado(TENANT, "comercial")).isFalse();
        assertThat(adapter.moduloHabilitado(TENANT, "facturacion")).isFalse();
        verify(planRepository, never()).findById(PLAN_ID);
    }

    @Test
    @DisplayName("Sin override, se hereda del Plan (comportamiento historico) (Req 25.4)")
    void sinOverrideHeredaDelPlan() {
        Suscripcion susc = suscripcionActiva(); // sin asignarModulos => sin override
        Plan plan = PlanTestFactory.conModulos("Premium", 10, Set.of("comercial", "facturacion"));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        assertThat(adapter.moduloHabilitado(TENANT, "facturacion")).isTrue();
        assertThat(adapter.moduloHabilitado(TENANT, "inventario")).isFalse();
        // Aqui el Plan SI se consulta.
        verify(planRepository, org.mockito.Mockito.atLeastOnce()).findById(PLAN_ID);
    }
}