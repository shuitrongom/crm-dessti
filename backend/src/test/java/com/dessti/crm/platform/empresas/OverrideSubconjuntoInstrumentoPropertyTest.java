package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Prueba basada en propiedades (jqwik) de la <strong>Property 7</strong> del
 * diseno <em>Plan vs Suscripcion / Contratacion</em>: el override manual de
 * modulos de una Empresa es <strong>siempre un subconjunto</strong> de los
 * modulos habilitados por el instrumento contratado (Plan o Paquete de
 * suscripcion). Cualquier modulo ajeno al instrumento provoca rechazo (422).
 *
 * <p>Ejercita directamente las piezas puras de dominio que enforzan la regla,
 * sin base de datos ni contexto de Spring:
 * {@link ModulosPlanValidacion#exigirSubconjuntoDelPlan(Set, Plan)} (caso PLAN) y
 * {@link ModulosPlanValidacion#exigirSubconjuntoDelPaquete(Set, PaqueteSuscripcion)}
 * (caso SUSCRIPCION). Ambas son la logica que
 * {@link ServicioSuscripciones#actualizarModulosEmpresa} invoca via
 * {@code exigirSubconjuntoDelInstrumento} (Req 11.1, 11.2).</p>
 *
 * <p>Los modulos habilitados de un instrumento se derivan de las claves de su
 * mapa de precios (ver {@code Plan#aplicarPrecios} /
 * {@code PaqueteSuscripcion#aplicarPrecios}). Por eso los generadores construyen
 * el instrumento a partir del conjunto de modulos permitidos, y el override se
 * genera de un universo fijo de claves en minusculas (para que la normalizacion
 * del validador sea neutra y la propiedad bajo prueba sea puramente la de
 * subconjunto).</p>
 */
class OverrideSubconjuntoInstrumentoPropertyTest {

    /** Moneda ISO 4217 valida (aisla la regla bajo prueba). */
    private static final String MONEDA = "MXN";

    /** Actor simbolico para las fabricas de dominio. */
    private static final String ACTOR = "super_admin";

    /** Duracion valida de Plan (> 365) y de Paquete (0 < d <= 365) respectivamente. */
    private static final int DURACION_PLAN = 400;
    private static final int DURACION_PAQUETE = 180;

    /**
     * Universo fijo de claves de modulo (en minusculas) del que se muestrean
     * tanto los modulos del instrumento como el override. Asegura que se generen
     * con frecuencia casos subconjunto y casos con modulos ajenos.
     */
    private static final List<String> UNIVERSO_MODULOS = List.of(
            "comercial", "facturacion", "compras", "inventario", "produccion-industrial");

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> giros() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /** Subconjuntos arbitrarios (posiblemente vacios) del universo de modulos. */
    @Provide
    Arbitrary<Set<String>> subconjuntosDeModulos() {
        return Arbitraries.subsetOf(UNIVERSO_MODULOS);
    }

    /** Construye el mapa de precios (clave = modulo) que fija los modulos del instrumento. */
    private static Map<String, BigDecimal> preciosDe(Set<String> modulos) {
        Map<String, BigDecimal> precios = new LinkedHashMap<>();
        for (String modulo : modulos) {
            precios.put(modulo, new BigDecimal("100.00"));
        }
        return precios;
    }

    private static Plan planCon(Set<String> modulos, UUID giroId) {
        return Plan.crear("Plan prueba", 10, DURACION_PLAN, giroId, MONEDA, preciosDe(modulos), ACTOR);
    }

    private static PaqueteSuscripcion paqueteCon(Set<String> modulos, UUID giroId) {
        return PaqueteSuscripcion.crear(
                "Paq prueba", 10, giroId, MONEDA, preciosDe(modulos), DURACION_PAQUETE, false, null, ACTOR);
    }

    // ----------------------------------------------------------------------
    // Property 7 (caso PLAN)
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 7: El override es siempre subconjunto del instrumento
    @Property(tries = 200)
    void overrideSubconjuntoDelPlanSeAceptaOSeRechaza(
            @ForAll("subconjuntosDeModulos") Set<String> modulosInstrumento,
            @ForAll("subconjuntosDeModulos") Set<String> override,
            @ForAll("giros") UUID giroId) {
        Plan plan = planCon(modulosInstrumento, giroId);
        boolean esSubconjunto = modulosInstrumento.containsAll(override);

        if (esSubconjunto) {
            assertThatCode(() -> ModulosPlanValidacion.exigirSubconjuntoDelPlan(override, plan))
                    .as("override %s subconjunto de %s debe aceptarse", override, modulosInstrumento)
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> ModulosPlanValidacion.exigirSubconjuntoDelPlan(override, plan))
                    .as("override %s con modulos ajenos a %s debe rechazarse (422)",
                            override, modulosInstrumento)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    // ----------------------------------------------------------------------
    // Property 7 (caso SUSCRIPCION / Paquete)
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 7: El override es siempre subconjunto del instrumento
    @Property(tries = 200)
    void overrideSubconjuntoDelPaqueteSeAceptaOSeRechaza(
            @ForAll("subconjuntosDeModulos") Set<String> modulosInstrumento,
            @ForAll("subconjuntosDeModulos") Set<String> override,
            @ForAll("giros") UUID giroId) {
        PaqueteSuscripcion paquete = paqueteCon(modulosInstrumento, giroId);
        boolean esSubconjunto = modulosInstrumento.containsAll(override);

        if (esSubconjunto) {
            assertThatCode(() -> ModulosPlanValidacion.exigirSubconjuntoDelPaquete(override, paquete))
                    .as("override %s subconjunto de %s debe aceptarse", override, modulosInstrumento)
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> ModulosPlanValidacion.exigirSubconjuntoDelPaquete(override, paquete))
                    .as("override %s con modulos ajenos a %s debe rechazarse (422)",
                            override, modulosInstrumento)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    // ----------------------------------------------------------------------
    // Property 7: semantica null = heredar del instrumento (nunca se valida)
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 7: El override es siempre subconjunto del instrumento
    @Property(tries = 100)
    void overrideNuloHeredaSiempreDelInstrumento(
            @ForAll("subconjuntosDeModulos") Set<String> modulosInstrumento,
            @ForAll("giros") UUID giroId) {
        Plan plan = planCon(modulosInstrumento, giroId);
        PaqueteSuscripcion paquete = paqueteCon(modulosInstrumento, giroId);

        assertThatCode(() -> ModulosPlanValidacion.exigirSubconjuntoDelPlan(null, plan))
                .as("override null (heredar del Plan) nunca debe rechazarse")
                .doesNotThrowAnyException();
        assertThatCode(() -> ModulosPlanValidacion.exigirSubconjuntoDelPaquete(null, paquete))
                .as("override null (heredar del Paquete) nunca debe rechazarse")
                .doesNotThrowAnyException();
    }
}
