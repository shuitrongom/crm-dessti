package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de ejemplos y bordes de la <strong>dependencia de modulos</strong>
 * ({@code inventario-avanzado} &rarr; {@code operacion}) aplicada al persistir un
 * {@link Plan} (metodo {@code aplicarPrecios}, Req 7.1, 7.3, 7.4, 8.1).
 *
 * <p>Complementan la property test {@code PlanDependenciaPropertyTest} (Property 4)
 * con casos concretos: alta con {@code inventario-avanzado}, respeto del precio
 * capturado para {@code operacion}, unidireccionalidad y ausencia de la
 * dependencia cuando no hay {@code inventario-avanzado}.</p>
 */
class PlanDependenciaModulosTest {

    /** Moneda ISO 4217 valida (aisla la regla bajo prueba). */
    private static final String MONEDA = "MXN";

    /** Actor (super_admin) simbolico para las factorias de dominio. */
    private static final String ACTOR = "super_admin";

    /** Giro ficticio no nulo (obligatorio en la factoria). */
    private static final UUID GIRO_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    /** Duracion valida para un Plan (debe ser {@code > 365}). */
    private static final int DURACION_PLAN = 730;

    private static Map<String, BigDecimal> precios(Object... clavesYPrecios) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        for (int i = 0; i < clavesYPrecios.length; i += 2) {
            mapa.put((String) clavesYPrecios[i], new BigDecimal((String) clavesYPrecios[i + 1]));
        }
        return mapa;
    }

    @Test
    @DisplayName("Plan con inventario-avanzado (y otros modulos) habilita operacion con precio 0.00")
    void planConInventarioAvanzadoAgregaOperacionGratis() {
        Plan plan = Plan.crear("Plan Pro", 10, DURACION_PLAN, GIRO_ID, MONEDA,
                precios("comercial", "100.00", "inventario-avanzado", "50.00"), ACTOR);

        // La dependencia agrega 'operacion'; los demas modulos se conservan.
        assertThat(plan.getModulosHabilitados())
                .contains("operacion")
                .contains("comercial", "inventario-avanzado");
        // 'operacion' queda con precio 0.00 (escala monetaria).
        assertThat(plan.getPreciosModulos())
                .containsEntry("operacion", new BigDecimal("0.00"));
        // Coherencia de claves: claves(precios) == modulos_habilitados.
        assertThat(plan.getPreciosModulos().keySet())
                .containsExactlyInAnyOrderElementsOf(plan.getModulosHabilitados());
    }

    @Test
    @DisplayName("Con operacion ya presente y precio capturado, no se duplica ni se sobrescribe")
    void planRespetaPrecioCapturadoDeOperacion() {
        Plan plan = Plan.crear("Plan Pro", 10, DURACION_PLAN, GIRO_ID, MONEDA,
                precios("operacion", "50.00", "inventario-avanzado", "80.00"), ACTOR);

        // 'operacion' aparece una sola vez y conserva el precio capturado (50.00).
        assertThat(plan.getModulosHabilitados()).filteredOn("operacion"::equals).hasSize(1);
        assertThat(plan.getPreciosModulos())
                .containsEntry("operacion", new BigDecimal("50.00"));
        assertThat(plan.getPreciosModulos().keySet())
                .containsExactlyInAnyOrderElementsOf(plan.getModulosHabilitados());
    }

    @Test
    @DisplayName("Solo operacion (sin inventario-avanzado) no agrega inventario-avanzado: unidireccional")
    void planConSoloOperacionEsUnidireccional() {
        Plan plan = Plan.crear("Plan Base", 5, DURACION_PLAN, GIRO_ID, MONEDA,
                precios("operacion", "50.00"), ACTOR);

        assertThat(plan.getModulosHabilitados())
                .contains("operacion")
                .doesNotContain("inventario-avanzado");
    }

    @Test
    @DisplayName("Sin inventario-avanzado no se agrega operacion")
    void planSinInventarioAvanzadoNoAgregaOperacion() {
        Plan plan = Plan.crear("Plan Base", 5, DURACION_PLAN, GIRO_ID, MONEDA,
                precios("comercial", "100.00"), ACTOR);

        assertThat(plan.getModulosHabilitados())
                .contains("comercial")
                .doesNotContain("operacion");
        assertThat(plan.getPreciosModulos().keySet())
                .containsExactlyInAnyOrderElementsOf(plan.getModulosHabilitados());
    }
}
