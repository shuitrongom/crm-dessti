package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>dependencia de modulos</strong>
 * {@code inventario-avanzado} &rarr; {@code operacion} aplicada al persistir un
 * {@link Plan} o un {@link PaqueteSuscripcion}
 * (feature {@code operacion-inventario-modulo-dependiente}).
 *
 * <p>Cubre la <strong>Property 4</strong> del diseño: para todo Plan/Paquete
 * construido (via {@code crear(...)}) cuyo mapa de precios <em>incluye</em>
 * {@code inventario-avanzado} (junto a otros modulos aleatorios con precios
 * validos), {@link Plan#getModulosHabilitados()} /
 * {@link PaqueteSuscripcion#getModulosHabilitados()} incluye {@code operacion}, y
 * las claves de {@code getPreciosModulos()} coinciden <strong>exactamente</strong>
 * (mismo conjunto) con {@code getModulosHabilitados()} — invariante de coherencia
 * claves&harr;precios.</p>
 *
 * <p>Los generadores ejercitan directamente las piezas puras de dominio, sin base
 * de datos ni contexto de Spring, con &ge; 100 iteraciones por propiedad. Para
 * aislar la regla bajo prueba se fija una moneda valida ({@code "MXN"}) y un Giro
 * no nulo; se generan modulos aleatorios de un catalogo conocido (siempre con
 * {@code inventario-avanzado} presente) y precios {@link BigDecimal} validos
 * (escala 2, dentro del rango que acepta {@code MonetizacionValidaciones}). Un
 * Plan exige {@code duracionDias > 365}; un Paquete exige
 * {@code 0 < duracionDias <= 365} con {@code admitePrueba = false}.</p>
 */
class PlanDependenciaPropertyTest {

    /** Moneda ISO 4217 valida usada por los generadores (aisla la regla bajo prueba). */
    private static final String MONEDA = "MXN";

    /** Actor (super_admin) simbolico para las fabricas de dominio. */
    private static final String ACTOR = "super_admin";

    /** Clave del modulo dependiente que arrastra la dependencia. */
    private static final String INVENTARIO_AVANZADO = "inventario-avanzado";

    /** Clave del modulo requerido por la dependencia. */
    private static final String OPERACION = "operacion";

    /**
     * Catalogo de claves de modulo conocidas (ademas de {@code inventario-avanzado},
     * siempre presente) del que se eligen modulos aleatorios adicionales. Se
     * excluyen a proposito {@code inventario-avanzado} y {@code operacion} para
     * controlar su presencia explicitamente en el generador.
     */
    private static final List<String> OTROS_MODULOS = List.of(
            "comercial", "estrategia", "redes-sociales", "contabilidad",
            "rh-nomina", "tesoreria", "compras", "facturacion");

    // ----------------------------------------------------------------------
    // Generadores comunes
    // ----------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> giros() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * Genera un precio {@link BigDecimal} valido: escala 2, dentro del rango
     * cerrado [0.00, 999,999,999.99] que acepta {@code MonetizacionValidaciones}.
     * Se acota a un rango holgado pero pequeño para legibilidad; siempre valido.
     */
    @Provide
    Arbitrary<BigDecimal> precios() {
        return Arbitraries.longs().between(0L, 100_000_00L)
                .map(centavos -> BigDecimal.valueOf(centavos, 2));
    }

    /**
     * Genera un mapa de precios que <strong>siempre incluye</strong>
     * {@code inventario-avanzado}, mas un subconjunto aleatorio de otros modulos
     * conocidos con precios validos. Nunca incluye {@code operacion}: la
     * dependencia debe agregarlo. Preserva el orden de insercion.
     */
    @Provide
    Arbitrary<Map<String, BigDecimal>> preciosConInventarioAvanzado() {
        Arbitrary<List<String>> otros = Arbitraries.subsetOf(OTROS_MODULOS)
                .map(java.util.ArrayList::new);
        return otros.flatMap(claves -> precios().list().ofSize(claves.size() + 1)
                .map(valores -> {
                    Map<String, BigDecimal> mapa = new LinkedHashMap<>();
                    // El modulo dependiente siempre presente en cada caso.
                    mapa.put(INVENTARIO_AVANZADO, valores.get(0));
                    for (int i = 0; i < claves.size(); i++) {
                        mapa.put(claves.get(i), valores.get(i + 1));
                    }
                    return mapa;
                }));
    }

    // ----------------------------------------------------------------------
    // Property 4: Persistir un Plan/Paquete con inventario-avanzado habilita operacion
    // ----------------------------------------------------------------------

    // Feature: operacion-inventario-modulo-dependiente, Property 4:
    // Persistir un Plan/Paquete con inventario-avanzado habilita operacion
    @Property(tries = 200)
    @Label("Feature: operacion-inventario-modulo-dependiente, Property 4: "
            + "Persistir un Plan/Paquete con inventario-avanzado habilita operacion")
    void planConInventarioAvanzadoHabilitaOperacion(
            @ForAll("preciosConInventarioAvanzado") Map<String, BigDecimal> precios,
            @ForAll @IntRange(min = 366, max = 3650) int duracionDias,
            @ForAll("giros") UUID giroId) {
        Plan plan = Plan.crear("Plan dependencia", 10, duracionDias, giroId, MONEDA, precios, ACTOR);

        // La dependencia agrega 'operacion' a los modulos habilitados.
        assertThat(plan.getModulosHabilitados())
                .as("un Plan con '%s' debe habilitar '%s'", INVENTARIO_AVANZADO, OPERACION)
                .contains(INVENTARIO_AVANZADO, OPERACION);

        // Invariante de coherencia: las claves de precios coinciden EXACTAMENTE
        // (mismo conjunto) con los modulos habilitados.
        assertThat(plan.getPreciosModulos().keySet())
                .as("las claves de precios deben coincidir exactamente con los modulos habilitados")
                .containsExactlyInAnyOrderElementsOf(plan.getModulosHabilitados());
    }

    // Feature: operacion-inventario-modulo-dependiente, Property 4:
    // Persistir un Plan/Paquete con inventario-avanzado habilita operacion
    @Property(tries = 200)
    @Label("Feature: operacion-inventario-modulo-dependiente, Property 4: "
            + "Persistir un Plan/Paquete con inventario-avanzado habilita operacion")
    void paqueteConInventarioAvanzadoHabilitaOperacion(
            @ForAll("preciosConInventarioAvanzado") Map<String, BigDecimal> precios,
            @ForAll @IntRange(min = 1, max = 365) int duracionDias,
            @ForAll("giros") UUID giroId) {
        PaqueteSuscripcion paquete = PaqueteSuscripcion.crear(
                "Paquete dependencia", 10, giroId, MONEDA, precios, duracionDias, false, null, ACTOR);

        // La dependencia agrega 'operacion' a los modulos habilitados.
        assertThat(paquete.getModulosHabilitados())
                .as("un Paquete con '%s' debe habilitar '%s'", INVENTARIO_AVANZADO, OPERACION)
                .contains(INVENTARIO_AVANZADO, OPERACION);

        // Invariante de coherencia: las claves de precios coinciden EXACTAMENTE
        // (mismo conjunto) con los modulos habilitados.
        assertThat(paquete.getPreciosModulos().keySet())
                .as("las claves de precios deben coincidir exactamente con los modulos habilitados")
                .containsExactlyInAnyOrderElementsOf(paquete.getModulosHabilitados());
    }
}
