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
 * {@link PaqueteSuscripcion} (metodo {@code aplicarPrecios}, Req 7.1, 7.3, 7.4,
 * 8.1).
 *
 * <p>Espejan {@code PlanDependenciaModulosTest} para el instrumento de corto
 * plazo: alta con {@code inventario-avanzado}, respeto del precio capturado para
 * {@code operacion}, unidireccionalidad y ausencia de la dependencia cuando no
 * hay {@code inventario-avanzado}.</p>
 */
class PaqueteSuscripcionDependenciaModulosTest {

    /** Moneda ISO 4217 valida (aisla la regla bajo prueba). */
    private static final String MONEDA = "MXN";

    /** Actor (super_admin) simbolico para las factorias de dominio. */
    private static final String ACTOR = "super_admin";

    /** Giro ficticio no nulo (obligatorio en la factoria). */
    private static final UUID GIRO_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    /** Duracion valida para un Paquete (debe estar entre 1 y 365). */
    private static final int DURACION_PAQUETE = 180;

    private static Map<String, BigDecimal> precios(Object... clavesYPrecios) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        for (int i = 0; i < clavesYPrecios.length; i += 2) {
            mapa.put((String) clavesYPrecios[i], new BigDecimal((String) clavesYPrecios[i + 1]));
        }
        return mapa;
    }

    private static PaqueteSuscripcion crear(Map<String, BigDecimal> precios) {
        // Sin periodo de prueba: aisla la regla de dependencia de modulos.
        return PaqueteSuscripcion.crear("Paquete", 10, GIRO_ID, MONEDA, precios,
                DURACION_PAQUETE, false, null, ACTOR);
    }

    @Test
    @DisplayName("Paquete con inventario-avanzado (y otros modulos) habilita operacion con precio 0.00")
    void paqueteConInventarioAvanzadoAgregaOperacionGratis() {
        PaqueteSuscripcion paquete = crear(
                precios("comercial", "100.00", "inventario-avanzado", "50.00"));

        assertThat(paquete.getModulosHabilitados())
                .contains("operacion")
                .contains("comercial", "inventario-avanzado");
        assertThat(paquete.getPreciosModulos())
                .containsEntry("operacion", new BigDecimal("0.00"));
        assertThat(paquete.getPreciosModulos().keySet())
                .containsExactlyInAnyOrderElementsOf(paquete.getModulosHabilitados());
    }

    @Test
    @DisplayName("Con operacion ya presente y precio capturado, no se duplica ni se sobrescribe")
    void paqueteRespetaPrecioCapturadoDeOperacion() {
        PaqueteSuscripcion paquete = crear(
                precios("operacion", "50.00", "inventario-avanzado", "80.00"));

        assertThat(paquete.getModulosHabilitados()).filteredOn("operacion"::equals).hasSize(1);
        assertThat(paquete.getPreciosModulos())
                .containsEntry("operacion", new BigDecimal("50.00"));
        assertThat(paquete.getPreciosModulos().keySet())
                .containsExactlyInAnyOrderElementsOf(paquete.getModulosHabilitados());
    }

    @Test
    @DisplayName("Solo operacion (sin inventario-avanzado) no agrega inventario-avanzado: unidireccional")
    void paqueteConSoloOperacionEsUnidireccional() {
        PaqueteSuscripcion paquete = crear(precios("operacion", "50.00"));

        assertThat(paquete.getModulosHabilitados())
                .contains("operacion")
                .doesNotContain("inventario-avanzado");
    }

    @Test
    @DisplayName("Sin inventario-avanzado no se agrega operacion")
    void paqueteSinInventarioAvanzadoNoAgregaOperacion() {
        PaqueteSuscripcion paquete = crear(precios("comercial", "100.00"));

        assertThat(paquete.getModulosHabilitados())
                .contains("comercial")
                .doesNotContain("operacion");
        assertThat(paquete.getPreciosModulos().keySet())
                .containsExactlyInAnyOrderElementsOf(paquete.getModulosHabilitados());
    }
}
