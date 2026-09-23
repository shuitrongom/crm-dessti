package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Pruebas basadas en propiedades (jqwik) de las invariantes y estados de dominio
 * del rediseño <strong>Plan vs Suscripción / Contratación</strong>
 * (feature {@code plan-vs-suscripcion-contratacion}).
 *
 * <p>Ejercitan directamente las piezas puras de dominio ({@link Plan},
 * {@link PaqueteSuscripcion}, {@link Suscripcion} y {@link EstadoSuscripcion}),
 * sin base de datos ni contexto de Spring, cubriendo las Correctness Properties
 * 1, 2, 3, 4, 8, 9 y 10 del documento de diseño (cada una con &ge; 100
 * iteraciones).</p>
 *
 * <p>Constantes de dominio usadas por los generadores: un {@link Plan} exige
 * {@code duracionDias > 365}; un {@link PaqueteSuscripcion} exige
 * {@code 0 < duracionDias <= 365}; la prueba (en días, aproximando 30 días/mes)
 * no puede exceder la duración del contrato. Para aislar la regla bajo prueba,
 * los generadores fijan una moneda válida ({@code "MXN"}), un Giro no nulo y un
 * mapa de precios vacío (los casos de moneda/giro/precios se validan en otras
 * pruebas).</p>
 */
class PlanVsSuscripcionDominioPropertyTest {

    /** Moneda ISO 4217 válida usada por los generadores (aísla la regla bajo prueba). */
    private static final String MONEDA = "MXN";

    /** Actor (super_admin) simbólico para las fábricas de dominio. */
    private static final String ACTOR = "super_admin";

    /** Aproximación de días por mes usada por el dominio para la prueba (Req 3.6). */
    private static final int DIAS_POR_MES = 30;

    // ----------------------------------------------------------------------
    // Generadores comunes
    // ----------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> giros() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<UUID> tenants() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<LocalDate> fechas() {
        return Combinators.combine(
                        Arbitraries.integers().between(2000, 2100),
                        Arbitraries.integers().between(1, 12),
                        Arbitraries.integers().between(1, 28))
                .as(LocalDate::of);
    }

    // ----------------------------------------------------------------------
    // Property 1: Exclusividad de instrumento (XOR)
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 1: Exclusividad de instrumento (XOR)
    @Property(tries = 200)
    void contratoDePlanTieneExactamentePlanId(
            @ForAll("tenants") UUID tenantId,
            @ForAll("giros") UUID planId,
            @ForAll("fechas") LocalDate inicio) {
        Suscripcion contrato = Suscripcion.crearDePlan(tenantId, planId, inicio, null, ACTOR);

        assertThat(contrato.getTipoInstrumento()).isEqualTo(TipoInstrumento.PLAN);
        assertThat(contrato.getPlanId()).isNotNull();
        assertThat(contrato.getPaqueteSuscripcionId()).isNull();
        // XOR: exactamente una referencia no nula, coherente con tipo_instrumento.
        assertThat(contrato.getPlanId() != null ^ contrato.getPaqueteSuscripcionId() != null).isTrue();
    }

    // Feature: plan-vs-suscripcion-contratacion, Property 1: Exclusividad de instrumento (XOR)
    @Property(tries = 200)
    void contratoDeSuscripcionTieneExactamentePaqueteId(
            @ForAll("tenants") UUID tenantId,
            @ForAll("giros") UUID paqueteId,
            @ForAll("fechas") LocalDate inicio) {
        Suscripcion contrato = Suscripcion.crearDeSuscripcion(tenantId, paqueteId, inicio, null, ACTOR);

        assertThat(contrato.getTipoInstrumento()).isEqualTo(TipoInstrumento.SUSCRIPCION);
        assertThat(contrato.getPaqueteSuscripcionId()).isNotNull();
        assertThat(contrato.getPlanId()).isNull();
        assertThat(contrato.getPlanId() != null ^ contrato.getPaqueteSuscripcionId() != null).isTrue();
    }

    // Feature: plan-vs-suscripcion-contratacion, Property 1: Exclusividad de instrumento (XOR)
    @Property(tries = 200)
    void contratoEnPruebaTieneExactamentePaqueteId(
            @ForAll("tenants") UUID tenantId,
            @ForAll("giros") UUID paqueteId,
            @ForAll("fechas") LocalDate inicio,
            @ForAll @IntRange(min = 1, max = 12) int pruebaMeses) {
        Suscripcion contrato = Suscripcion.crearEnPrueba(tenantId, paqueteId, inicio, pruebaMeses, ACTOR);

        assertThat(contrato.getTipoInstrumento()).isEqualTo(TipoInstrumento.SUSCRIPCION);
        assertThat(contrato.getPaqueteSuscripcionId()).isNotNull();
        assertThat(contrato.getPlanId()).isNull();
        assertThat(contrato.getPlanId() != null ^ contrato.getPaqueteSuscripcionId() != null).isTrue();
    }

    // ----------------------------------------------------------------------
    // Property 2: Duración del Plan siempre mayor a un año
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 2: Duración del Plan siempre mayor a un año
    @Property(tries = 200)
    void planSeAceptaSiYSoloSiDuracionMayorAUnAnio(
            @ForAll @IntRange(min = -50, max = 2000) int duracionDias,
            @ForAll("giros") UUID giroId) {
        boolean deberiaAceptarse = duracionDias > 365;

        if (deberiaAceptarse) {
            assertThatCode(() -> Plan.crear("Plan " + duracionDias, 10, duracionDias, giroId, MONEDA, null, ACTOR))
                    .as("un Plan con duracion %d (> 365) debe aceptarse", duracionDias)
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> Plan.crear("Plan " + duracionDias, 10, duracionDias, giroId, MONEDA, null, ACTOR))
                    .as("un Plan con duracion %d (<= 365) debe rechazarse", duracionDias)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    // Feature: plan-vs-suscripcion-contratacion, Property 2: Duración del Plan siempre mayor a un año
    @Property(tries = 200)
    void actualizarPlanRespetaLaMismaReglaDeDuracion(
            @ForAll @IntRange(min = -50, max = 2000) int nuevaDuracion,
            @ForAll("giros") UUID giroId) {
        // Se parte de un Plan válido (duracion 400) y se intenta actualizar.
        Plan plan = Plan.crear("Plan base", 10, 400, giroId, MONEDA, null, ACTOR);
        boolean deberiaAceptarse = nuevaDuracion > 365;

        if (deberiaAceptarse) {
            assertThatCode(() -> plan.actualizar("Plan base", 10, nuevaDuracion, giroId, MONEDA, null, ACTOR))
                    .doesNotThrowAnyException();
            assertThat(plan.getDuracionDias()).isEqualTo(nuevaDuracion);
        } else {
            assertThatThrownBy(() -> plan.actualizar("Plan base", 10, nuevaDuracion, giroId, MONEDA, null, ACTOR))
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    // ----------------------------------------------------------------------
    // Property 3: Duración del Paquete siempre de un año o menos
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 3: Duración del Paquete siempre de un año o menos
    @Property(tries = 200)
    void paqueteSeAceptaSiYSoloSiDuracionEntre1Y365(
            @ForAll @IntRange(min = -50, max = 800) int duracionDias,
            @ForAll("giros") UUID giroId) {
        boolean deberiaAceptarse = duracionDias > 0 && duracionDias <= 365;

        if (deberiaAceptarse) {
            assertThatCode(() -> PaqueteSuscripcion.crear(
                    "Paq " + duracionDias, 10, giroId, MONEDA, null, duracionDias, false, null, ACTOR))
                    .as("un Paquete con duracion %d en (0,365] debe aceptarse", duracionDias)
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> PaqueteSuscripcion.crear(
                    "Paq " + duracionDias, 10, giroId, MONEDA, null, duracionDias, false, null, ACTOR))
                    .as("un Paquete con duracion %d fuera de (0,365] debe rechazarse", duracionDias)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    // ----------------------------------------------------------------------
    // Property 4: Coherencia de la configuración de prueba
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 4: Coherencia de la configuración de prueba
    @Property(tries = 300)
    void configuracionDePruebaCoherenteSeAceptaOSeRechaza(
            @ForAll @IntRange(min = 1, max = 365) int duracionDias,
            @ForAll boolean admitePrueba,
            @ForAll @IntRange(min = -3, max = 20) int pruebaMesesRaw,
            @ForAll("giros") UUID giroId) {
        // Cuando admitePrueba es false, la duración de prueba se ignora (puede ser cualquier valor).
        Integer pruebaMeses = admitePrueba ? Integer.valueOf(pruebaMesesRaw) : null;

        boolean pruebaValida;
        if (!admitePrueba) {
            pruebaValida = true; // se ignora la config de prueba
        } else {
            pruebaValida = pruebaMesesRaw > 0
                    && (long) pruebaMesesRaw * DIAS_POR_MES <= duracionDias;
        }

        if (pruebaValida) {
            PaqueteSuscripcion paquete = PaqueteSuscripcion.crear(
                    "Paq", 10, giroId, MONEDA, null, duracionDias, admitePrueba, pruebaMeses, ACTOR);
            assertThat(paquete.isAdmitePrueba()).isEqualTo(admitePrueba);
            if (admitePrueba) {
                assertThat(paquete.getDuracionPruebaMeses()).isEqualTo(pruebaMeses);
            } else {
                // Si no admite prueba, la duración de prueba queda anulada (Req 3.5/3.6).
                assertThat(paquete.getDuracionPruebaMeses()).isNull();
            }
        } else {
            assertThatThrownBy(() -> PaqueteSuscripcion.crear(
                    "Paq", 10, giroId, MONEDA, null, duracionDias, admitePrueba, pruebaMeses, ACTOR))
                    .as("config de prueba incoherente (meses=%s, dias=%d) debe rechazarse",
                            pruebaMeses, duracionDias)
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    // ----------------------------------------------------------------------
    // Property 8: Activar facturación solo desde EN_PRUEBA
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 8: Activar facturación solo desde EN_PRUEBA
    @Property(tries = 200)
    void activarFacturacionDesdeEnPruebaTransicionaAActiva(
            @ForAll("tenants") UUID tenantId,
            @ForAll("giros") UUID paqueteId,
            @ForAll("fechas") LocalDate inicio,
            @ForAll @IntRange(min = 1, max = 6) int pruebaMeses) {
        Suscripcion contrato = Suscripcion.crearEnPrueba(tenantId, paqueteId, inicio, pruebaMeses, ACTOR);
        LocalDate inicioFacturacion = inicio.plusDays(1);
        LocalDate nuevaVigenciaFin = inicio.plusMonths(pruebaMeses).plusMonths(1);

        contrato.activarFacturacion(inicioFacturacion, nuevaVigenciaFin, ACTOR);

        assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(contrato.isFacturacionActivada()).isTrue();
        assertThat(contrato.getInicioFacturacion()).isEqualTo(inicioFacturacion);
        assertThat(contrato.getVigenciaFin()).isEqualTo(nuevaVigenciaFin);
    }

    // Feature: plan-vs-suscripcion-contratacion, Property 8: Activar facturación solo desde EN_PRUEBA
    @Property(tries = 200)
    void activarFacturacionDesdeEstadoDistintoDeEnPruebaSeRechaza(
            @ForAll("tenants") UUID tenantId,
            @ForAll("giros") UUID paqueteId,
            @ForAll("fechas") LocalDate inicio,
            @ForAll("estadosNoEnPrueba") EstadoSuscripcion estadoInicial) {
        // Contrato de suscripción ACTIVA que llevamos al estado destino != EN_PRUEBA.
        Suscripcion contrato = Suscripcion.crearDeSuscripcion(tenantId, paqueteId, inicio, null, ACTOR);
        llevarAEstado(contrato, estadoInicial);
        assertThat(contrato.getEstado()).isEqualTo(estadoInicial);

        assertThatThrownBy(() -> contrato.activarFacturacion(inicio.plusDays(1), null, ACTOR))
                .as("activar facturacion desde %s debe rechazarse (solo EN_PRUEBA)", estadoInicial)
                .isInstanceOf(ReglaNegocioException.class);
        // El estado no debe cambiar tras el rechazo.
        assertThat(contrato.getEstado()).isEqualTo(estadoInicial);
        assertThat(contrato.isFacturacionActivada()).isFalse();
    }

    /** Estados de contrato distintos de EN_PRUEBA, alcanzables desde ACTIVA. */
    @Provide
    Arbitrary<EstadoSuscripcion> estadosNoEnPrueba() {
        return Arbitraries.of(
                EstadoSuscripcion.ACTIVA,
                EstadoSuscripcion.SUSPENDIDA,
                EstadoSuscripcion.CANCELADA);
    }

    /** Lleva un contrato ACTIVA al estado destino usando las transiciones de dominio. */
    private static void llevarAEstado(Suscripcion contrato, EstadoSuscripcion destino) {
        switch (destino) {
            case ACTIVA -> { /* ya está ACTIVA */ }
            case SUSPENDIDA -> contrato.suspender(ACTOR);
            case CANCELADA -> contrato.cancelar(ACTOR);
            default -> throw new IllegalArgumentException("Estado no alcanzable en este test: " + destino);
        }
    }

    // ----------------------------------------------------------------------
    // Property 9: Otorgar prueba fija la vigencia correcta
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 9: Otorgar prueba fija la vigencia correcta
    @Property(tries = 200)
    void crearEnPruebaFijaEstadoYVigenciaFin(
            @ForAll("tenants") UUID tenantId,
            @ForAll("giros") UUID paqueteId,
            @ForAll("fechas") LocalDate inicio,
            @ForAll @IntRange(min = 1, max = 12) int pruebaMeses) {
        Suscripcion contrato = Suscripcion.crearEnPrueba(tenantId, paqueteId, inicio, pruebaMeses, ACTOR);

        assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.EN_PRUEBA);
        assertThat(contrato.getVigenciaInicio()).isEqualTo(inicio);
        assertThat(contrato.getVigenciaFin()).isEqualTo(inicio.plusMonths(pruebaMeses));
        assertThat(contrato.isFacturacionActivada()).isFalse();
    }

    // Feature: plan-vs-suscripcion-contratacion, Property 9: Otorgar prueba fija la vigencia correcta
    @Property(tries = 200)
    void crearEnPruebaConMesesNoPositivosSeRechaza(
            @ForAll("tenants") UUID tenantId,
            @ForAll("giros") UUID paqueteId,
            @ForAll("fechas") LocalDate inicio,
            @ForAll @IntRange(min = -12, max = 0) int pruebaMeses) {
        assertThatThrownBy(() -> Suscripcion.crearEnPrueba(tenantId, paqueteId, inicio, pruebaMeses, ACTOR))
                .as("crearEnPrueba con %d meses (<= 0) debe rechazarse", pruebaMeses)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // ----------------------------------------------------------------------
    // Property 10: Estabilidad del enum de estado persistido (round-trip)
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 10: Estabilidad del enum de estado persistido
    @Property(tries = 200)
    void roundTripEstadoSuscripcion(@ForAll("estados") EstadoSuscripcion estado) {
        String etiqueta = estado.valorBd();

        // desdeValorBd(valorBd(x)) == x
        assertThat(EstadoSuscripcion.desdeValorBd(etiqueta)).isEqualTo(estado);
        // La etiqueta persistida es minúsculas coherente con el CHECK de BD (Req 12.5).
        assertThat(etiqueta).isEqualTo(etiqueta.toLowerCase(java.util.Locale.ROOT));
        assertThat(etiqueta).isNotBlank();
        // Tolerancia a mayúsculas/espacios también resuelve al mismo estado.
        assertThat(EstadoSuscripcion.desdeValorBd("  " + etiqueta.toUpperCase(java.util.Locale.ROOT) + "  "))
                .isEqualTo(estado);
    }

    /** Todos los valores del enum de estado, incluidos EN_PRUEBA y VENCIDA (Req 5.1). */
    @Provide
    Arbitrary<EstadoSuscripcion> estados() {
        return Arbitraries.of(EstadoSuscripcion.values());
    }
}
