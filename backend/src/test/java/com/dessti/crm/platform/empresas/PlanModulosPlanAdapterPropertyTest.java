package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantSessionInitializer;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) del adaptador REAL de gating de modulos
 * {@link PlanModulosPlanAdapter}, centradas en la red de seguridad defensiva
 * (D7-b) que aplica el cierre transitivo de
 * {@code CatalogoDependenciasModulos.normalizar(...)} sobre la lista efectiva de
 * modulos antes de exponerla en el claim
 * (feature {@code operacion-inventario-modulo-dependiente}).
 *
 * <p>Cubre la <strong>Property 5</strong> del diseño: para toda suscripcion
 * <em>vigente</em> (estado ACTIVA o EN_PRUEBA, no vencida) cuya lista efectiva de
 * modulos —resuelta por cualquiera de los tres caminos: override presente,
 * herencia de {@link Plan} o herencia de {@link PaqueteSuscripcion}— <strong>incluye</strong>
 * {@code inventario-avanzado}, {@link PlanModulosPlanAdapter#modulosHabilitadosDe(UUID)}
 * <strong>incluye</strong> {@code operacion}, aunque la fuente traiga datos legados
 * sin normalizar (Req 8.2, 8.4, 11.1, 11.2).</p>
 *
 * <p>La prueba trabaja con dobles de Mockito de los repositorios y del
 * {@link TenantSessionInitializer} (no-op), con un {@link Clock} fijo y una
 * suscripcion cuya {@code vigenciaFin} es nula o futura (nunca vencida). El
 * generador elige aleatoriamente la fuente (override / Plan / Paquete) y un
 * conjunto de modulos que <strong>siempre</strong> contiene {@code inventario-avanzado}
 * (para ejercitar la rama que agrega {@code operacion}); a proposito NO incluye
 * {@code operacion} en la fuente para verificar que la normalizacion defensiva lo
 * agrega. Se ejecutan &ge; 100 iteraciones.</p>
 */
class PlanModulosPlanAdapterPropertyTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAQUETE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Reloj fijo; la vigenciaFin de la suscripcion sera nula o posterior a esta fecha. */
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2025-06-15T00:00:00Z"), ZoneOffset.UTC);

    /** Fecha "hoy" derivada del reloj fijo, para construir vigencias no vencidas. */
    private static final LocalDate HOY = LocalDate.now(CLOCK);

    private static final String ACTOR = "super_admin";
    private static final String INVENTARIO_AVANZADO = "inventario-avanzado";
    private static final String OPERACION = "operacion";
    private static final String MONEDA = "MXN";

    /**
     * Catalogo de otras claves de modulo (sin {@code inventario-avanzado} ni
     * {@code operacion}) del que el generador elige un subconjunto adicional para
     * acompañar a {@code inventario-avanzado} en la lista efectiva.
     */
    private static final List<String> OTROS_MODULOS = List.of(
            "comercial", "estrategia", "redes-sociales", "contabilidad",
            "rh-nomina", "tesoreria", "compras", "facturacion");

    /** Fuente que resuelve la lista efectiva de modulos en el adaptador. */
    private enum Fuente { OVERRIDE, PLAN, PAQUETE }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Genera la fuente de la lista efectiva de modulos. */
    @Provide
    Arbitrary<Fuente> fuentes() {
        return Arbitraries.of(Fuente.class);
    }

    /**
     * Genera un conjunto de modulos que <strong>siempre incluye</strong>
     * {@code inventario-avanzado} y NUNCA {@code operacion} (para forzar que la
     * normalizacion defensiva lo agregue). Ademas incluye un subconjunto aleatorio
     * de {@link #OTROS_MODULOS}, preservando el orden de insercion.
     */
    @Provide
    Arbitrary<List<String>> modulosConInventarioAvanzado() {
        return Arbitraries.subsetOf(OTROS_MODULOS)
                .map(otros -> {
                    Set<String> modulos = new LinkedHashSet<>();
                    modulos.add(INVENTARIO_AVANZADO);
                    modulos.addAll(otros);
                    return new ArrayList<>(modulos);
                });
    }

    // ----------------------------------------------------------------------
    // Property 5: El claim efectivo incluye 'operacion' mientras haya 'inventario-avanzado'
    // ----------------------------------------------------------------------

    // Feature: operacion-inventario-modulo-dependiente, Property 5:
    // El claim efectivo incluye operacion mientras haya inventario-avanzado
    @Property(tries = 200)
    @Label("Feature: operacion-inventario-modulo-dependiente, Property 5: "
            + "El claim efectivo incluye operacion mientras haya inventario-avanzado")
    void claimIncluyeOperacionMientrasHayaInventarioAvanzado(
            @ForAll("fuentes") Fuente fuente,
            @ForAll("modulosConInventarioAvanzado") List<String> modulosEfectivos,
            @ForAll boolean vigenciaFinNula) {

        // Dobles de los colaboradores; TenantSessionInitializer es un no-op
        // (mock sin stubs). La suscripcion vigente y el instrumento se resuelven
        // segun la fuente elegida.
        SuscripcionRepository suscripcionRepository = mock(SuscripcionRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        PaqueteSuscripcionRepository paqueteSuscripcionRepository = mock(PaqueteSuscripcionRepository.class);
        TenantSessionInitializer tenantSession = mock(TenantSessionInitializer.class);

        PlanModulosPlanAdapter adapter = new PlanModulosPlanAdapter(
                suscripcionRepository, planRepository, paqueteSuscripcionRepository,
                tenantSession, CLOCK);

        // vigenciaFin nula o futura respecto al reloj fijo: la suscripcion NUNCA
        // esta vencida, de modo que el adaptador resuelve la lista efectiva.
        LocalDate vigenciaFin = vigenciaFinNula ? null : HOY.plusMonths(6);

        Suscripcion suscripcion = construirSuscripcionVigente(
                fuente, modulosEfectivos, vigenciaFin,
                suscripcionRepository, planRepository, paqueteSuscripcionRepository);

        // Precondicion del generador: la lista efectiva contiene inventario-avanzado
        // y NO contiene operacion (la normalizacion defensiva debe agregarlo).
        assertThat(modulosEfectivos)
                .as("precondicion: la fuente contiene inventario-avanzado")
                .contains(INVENTARIO_AVANZADO);
        assertThat(modulosEfectivos)
                .as("precondicion: la fuente NO contiene operacion")
                .doesNotContain(OPERACION);

        List<String> claim = adapter.modulosHabilitadosDe(TENANT);

        // Property 5: el claim efectivo incluye operacion mientras haya
        // inventario-avanzado, y conserva inventario-avanzado.
        assertThat(claim)
                .as("el claim con '%s' debe incluir '%s' (fuente=%s)",
                        INVENTARIO_AVANZADO, OPERACION, fuente)
                .contains(INVENTARIO_AVANZADO, OPERACION);
        // Se preservan los demas modulos de la fuente (la dependencia no borra nada).
        assertThat(claim)
                .as("el claim conserva los modulos de la fuente")
                .containsAll(modulosEfectivos);
    }

    // ----------------------------------------------------------------------
    // Utilidades de construccion
    // ----------------------------------------------------------------------

    /**
     * Construye una suscripcion vigente (estado ACTIVA o EN_PRUEBA, no vencida) y
     * programa los mocks para que el adaptador resuelva la lista efectiva de
     * modulos por la fuente indicada:
     * <ul>
     *   <li>{@link Fuente#OVERRIDE}: la suscripcion define un override con los
     *       modulos efectivos (manda sobre el instrumento).</li>
     *   <li>{@link Fuente#PLAN}: sin override; el {@link Plan} referenciado tiene
     *       esos modulos.</li>
     *   <li>{@link Fuente#PAQUETE}: sin override; el {@link PaqueteSuscripcion}
     *       referenciado tiene esos modulos.</li>
     * </ul>
     *
     * <p>Para aislar la red de seguridad defensiva, en los caminos de herencia se
     * construye el instrumento con los modulos <em>tal cual</em> (que ya incluye
     * {@code operacion} por la normalizacion al persistir del dominio). Por eso, en
     * esos caminos, se fuerza el override en la suscripcion con la lista SIN
     * normalizar (con {@code inventario-avanzado} y sin {@code operacion}) solo
     * cuando la fuente es OVERRIDE; en PLAN/PAQUETE se usa el propio instrumento
     * como fuente legada simulando datos sin normalizar mediante un doble que
     * devuelve exactamente la lista dada.</p>
     */
    private Suscripcion construirSuscripcionVigente(
            Fuente fuente,
            List<String> modulosEfectivos,
            LocalDate vigenciaFin,
            SuscripcionRepository suscripcionRepository,
            PlanRepository planRepository,
            PaqueteSuscripcionRepository paqueteSuscripcionRepository) {

        Suscripcion suscripcion;
        switch (fuente) {
            case OVERRIDE -> {
                // Contrato de tipo PLAN, pero con override presente: la lista
                // efectiva es exactamente el override (datos legados sin normalizar).
                suscripcion = Suscripcion.crearDePlan(TENANT, PLAN_ID, HOY.minusMonths(1), vigenciaFin, ACTOR);
                // asignarModulos normaliza (recorte + minusculas + dedup) pero NO
                // aplica la dependencia de modulos; asi el override queda con
                // inventario-avanzado y sin operacion (fuente legada).
                suscripcion.asignarModulos(modulosEfectivos, ACTOR);
            }
            case PLAN -> {
                // Sin override: hereda del Plan. El Plan se simula con un doble que
                // devuelve la lista legada (sin operacion) para ejercitar la red
                // de seguridad defensiva del adaptador.
                suscripcion = Suscripcion.crearDePlan(TENANT, PLAN_ID, HOY.minusMonths(1), vigenciaFin, ACTOR);
                Plan plan = planLegado(modulosEfectivos);
                when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
            }
            case PAQUETE -> {
                // Sin override: hereda del Paquete de Suscripcion, tambien legado.
                suscripcion = Suscripcion.crearDeSuscripcion(
                        TENANT, PAQUETE_ID, HOY.minusMonths(1), vigenciaFin, ACTOR);
                PaqueteSuscripcion paquete = paqueteLegado(modulosEfectivos);
                when(paqueteSuscripcionRepository.findById(PAQUETE_ID)).thenReturn(Optional.of(paquete));
            }
            default -> throw new IllegalStateException("Fuente no soportada: " + fuente);
        }

        when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(
                TENANT, List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA)))
                .thenReturn(List.of(suscripcion));
        return suscripcion;
    }

    /**
     * Construye un {@link Plan} "legado" cuya lista de modulos habilitados es
     * exactamente la dada (con {@code inventario-avanzado} y sin {@code operacion}),
     * mediante un doble de Mockito. Se usa un doble en lugar de {@code Plan.crear}
     * porque el dominio ya normaliza la dependencia al persistir; aqui necesitamos
     * simular una fila historica sin normalizar para ejercitar el adaptador.
     */
    private Plan planLegado(List<String> modulos) {
        Plan plan = mock(Plan.class);
        when(plan.getModulosHabilitados()).thenReturn(List.copyOf(modulos));
        return plan;
    }

    /**
     * Construye un {@link PaqueteSuscripcion} "legado" analogo a {@link #planLegado}.
     */
    private PaqueteSuscripcion paqueteLegado(List<String> modulos) {
        PaqueteSuscripcion paquete = mock(PaqueteSuscripcion.class);
        when(paquete.getModulosHabilitados()).thenReturn(List.copyOf(modulos));
        return paquete;
    }

    /**
     * (Referencia) Construye un instrumento ya normalizado por el dominio, no usado
     * directamente en la property pero documentando la firma real de las fabricas.
     */
    @SuppressWarnings("unused")
    private static Map<String, BigDecimal> preciosDe(List<String> modulos) {
        Map<String, BigDecimal> precios = new LinkedHashMap<>();
        for (String modulo : modulos) {
            precios.put(modulo, new BigDecimal("0.00"));
        }
        return precios;
    }
}
