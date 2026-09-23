package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.security.usuarios.UsuarioRepository;

/**
 * Pruebas unitarias del adaptador REAL del limite de Usuarios por Plan
 * {@link LimiteUsuariosPlanAdapter} (Req 25.3):
 * <ul>
 *   <li>Permite crear cuando las cuentas activas son menores que {@code max_usuarios}.</li>
 *   <li>Deniega cuando se alcanzo el limite (activas == max).</li>
 *   <li>Denegacion por defecto sin Suscripcion activa o sin Plan.</li>
 * </ul>
 */
class LimiteUsuariosPlanAdapterTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private SuscripcionRepository suscripcionRepository;
    private PlanRepository planRepository;
    private UsuarioRepository usuarioRepository;
    private LimiteUsuariosPlanAdapter adapter;

    @BeforeEach
    void preparar() {
        suscripcionRepository = mock(SuscripcionRepository.class);
        planRepository = mock(PlanRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        adapter = new LimiteUsuariosPlanAdapter(suscripcionRepository, planRepository, usuarioRepository);
    }

    private void conPlan(int maxUsuarios) {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN_ID, LocalDate.of(2025, 1, 1), null, "super");
        Plan plan = PlanTestFactory.conModulos("Premium", maxUsuarios, Set.of("comercial"));
        when(suscripcionRepository.findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(susc));
        when(planRepository.findById(susc.getPlanId())).thenReturn(Optional.of(plan));
    }

    @Test
    @DisplayName("Permite crear cuando las cuentas activas son menores que el maximo (Req 25.3)")
    void permiteBajoElLimite() {
        conPlan(5);
        when(usuarioRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(4L);

        assertThat(adapter.puedeCrearUsuario(TENANT)).isTrue();
    }

    @Test
    @DisplayName("Deniega cuando se alcanzo el limite del Plan (activas == max, Req 25.3)")
    void deniegaEnElLimite() {
        conPlan(5);
        when(usuarioRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(5L);

        assertThat(adapter.puedeCrearUsuario(TENANT)).isFalse();
    }

    @Test
    @DisplayName("Un Plan con max_usuarios = 0 bloquea toda creacion (Req 25.3)")
    void deniegaConMaximoCero() {
        conPlan(0);
        when(usuarioRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);

        assertThat(adapter.puedeCrearUsuario(TENANT)).isFalse();
    }

    @Test
    @DisplayName("Deniega por defecto cuando no hay Suscripcion activa (Req 25.3)")
    void deniegaSinSuscripcionActiva() {
        when(suscripcionRepository.findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.empty());

        assertThat(adapter.puedeCrearUsuario(TENANT)).isFalse();
    }

    @Test
    @DisplayName("Deniega por defecto cuando el Plan no existe (Req 25.3)")
    void deniegaSinPlan() {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN_ID, LocalDate.of(2025, 1, 1), null, "super");
        when(suscripcionRepository.findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(susc));
        when(planRepository.findById(susc.getPlanId())).thenReturn(Optional.empty());

        assertThat(adapter.puedeCrearUsuario(TENANT)).isFalse();
    }
}
