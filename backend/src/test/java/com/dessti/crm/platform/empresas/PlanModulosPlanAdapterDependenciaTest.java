package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas unitarias de ejemplos/bordes de
 * {@link PlanModulosPlanAdapter#modulosHabilitadosDe(UUID)} centradas en la
 * normalizacion defensiva de la dependencia {@code inventario-avanzado ->
 * operacion} (tarea 3.10, red de seguridad D7-b) en el claim efectivo
 * (Req 8.2, 8.4, 11.1, 11.2):
 * <ul>
 *   <li>Override presente con {@code inventario-avanzado}: el claim incluye
 *       {@code operacion} aunque el override crudo no lo tenga (datos legados).</li>
 *   <li>Herencia de Plan con {@code inventario-avanzado}: el claim incluye
 *       {@code operacion}.</li>
 *   <li>Herencia de Paquete con {@code inventario-avanzado}: el claim incluye
 *       {@code operacion}.</li>
 *   <li>Sin contrato vigente: el claim queda vacio (no se fuerza operacion).</li>
 * </ul>
 *
 * <p>Copia el estilo/mocks de {@link PlanModulosPlanAdapterTest}: sin
 * {@code @SpringBootTest}, Clock fijo y repositorios mockeados.</p>
 */
class PlanModulosPlanAdapterDependenciaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAQUETE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

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

    private void conContratoVigente(Suscripcion susc) {
        when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(
                TENANT, List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA)))
                .thenReturn(List.of(susc));
    }

    @Test
    @DisplayName("Override con inventario-avanzado: el claim incluye operacion (Req 8.2, 8.4, 11.1, 11.2)")
    void overrideConInventarioAvanzadoIncluyeOperacion() {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN_ID, LocalDate.of(2025, 1, 1), null, "super");
        // Override crudo (dato legado) SIN operacion: solo inventario-avanzado.
        susc.asignarModulos(Set.of("inventario-avanzado"), "super");
        conContratoVigente(susc);

        List<String> claim = adapter.modulosHabilitadosDe(TENANT);

        assertThat(claim).contains("inventario-avanzado", "operacion");
    }

    @Test
    @DisplayName("Herencia de Plan con inventario-avanzado: el claim incluye operacion (Req 8.2, 8.4)")
    void herenciaDePlanIncluyeOperacion() {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN_ID, LocalDate.of(2025, 1, 1), null, "super");
        // Sin override (hereda). El Plan se construye directamente con un mapa de
        // precios que contiene inventario-avanzado sin operacion (dato legado);
        // la red de seguridad del adaptador debe agregar operacion en el claim.
        Plan plan = planConModulosCrudos("inventario-avanzado");
        conContratoVigente(susc);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        List<String> claim = adapter.modulosHabilitadosDe(TENANT);

        assertThat(claim).contains("inventario-avanzado", "operacion");
    }

    @Test
    @DisplayName("Herencia de Paquete con inventario-avanzado: el claim incluye operacion (Req 8.2, 8.4)")
    void herenciaDePaqueteIncluyeOperacion() {
        Suscripcion susc = Suscripcion.crearDeSuscripcion(
                TENANT, PAQUETE_ID, LocalDate.of(2025, 1, 1), null, "super");
        PaqueteSuscripcion paquete = paqueteConModulosCrudos("inventario-avanzado");
        conContratoVigente(susc);
        when(paqueteSuscripcionRepository.findById(PAQUETE_ID)).thenReturn(Optional.of(paquete));

        List<String> claim = adapter.modulosHabilitadosDe(TENANT);

        assertThat(claim).contains("inventario-avanzado", "operacion");
    }

    @Test
    @DisplayName("Sin contrato vigente: el claim queda vacio (no se fuerza operacion)")
    void sinContratoVigenteClaimVacio() {
        when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(
                TENANT, List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA)))
                .thenReturn(List.of());

        assertThat(adapter.modulosHabilitadosDe(TENANT)).isEmpty();
    }

    /**
     * Construye un Plan a partir de un mapa de precios que incluye los modulos
     * indicados. Sea porque {@link Plan#crear} ya normaliza la dependencia
     * (tarea 3.6) o porque lo haga la red de seguridad del adaptador (tarea
     * 3.10), el claim efectivo con {@code inventario-avanzado} debe terminar
     * incluyendo {@code operacion}. La prueba verifica el resultado final del
     * claim, no la ruta por la que se agrega.
     */
    private static Plan planConModulosCrudos(String... modulos) {
        Map<String, BigDecimal> precios = new java.util.LinkedHashMap<>();
        for (String m : modulos) {
            precios.put(m, new BigDecimal("0.00"));
        }
        return Plan.crear("Plan legado", 10, 730, PlanTestFactory.GIRO_ID, "MXN", precios, "super");
    }

    private static PaqueteSuscripcion paqueteConModulosCrudos(String... modulos) {
        Map<String, BigDecimal> precios = new java.util.LinkedHashMap<>();
        for (String m : modulos) {
            precios.put(m, new BigDecimal("0.00"));
        }
        return PaqueteSuscripcion.crear(
                "Paquete legado", 10, PlanTestFactory.GIRO_ID, "MXN", precios, 300, false, null, "super");
    }
}
