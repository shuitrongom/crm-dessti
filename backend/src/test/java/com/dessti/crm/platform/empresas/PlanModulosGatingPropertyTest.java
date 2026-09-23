package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.dessti.crm.platform.tenant.TenantSessionInitializer;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) del <strong>gating de modulos</strong>
 * del adaptador REAL {@link PlanModulosPlanAdapter} (feature
 * {@code plan-vs-suscripcion-contratacion}). Complementan a las pruebas por
 * ejemplo {@link PlanModulosPlanAdapterTest} y {@link PlanModulosOverrideAdapterTest},
 * ejercitando la resolucion de modulos habilitados a traves de muchos inputs.
 *
 * <p>Cubren las Correctness Properties 5 y 6 del documento de diseño (cada una
 * con &ge; 100 iteraciones). Se usa un {@link Clock} fijo en {@code 2025-06-15}
 * (UTC) para tener un "hoy" conocido y generar {@code vigenciaFin} relativas a
 * ese dia (antes, en el mismo dia, despues o {@code null}).</p>
 *
 * <h2>Semantica del corte por vencimiento (observada en produccion)</h2>
 * <p>{@link Suscripcion#estaVencida(LocalDate)} usa {@code vigenciaFin.isBefore(hoy)},
 * es decir un corte <strong>ESTRICTO</strong>: {@code vigenciaFin == hoy} NO esta
 * vencido (se concede acceso); solo {@code vigenciaFin < hoy} esta vencido (cero
 * modulos). Un {@code vigenciaFin == null} concede siempre.</p>
 *
 * <p>El adaptador selecciona el Contrato con
 * {@code findByTenantIdAndEstadoInOrderByIdAsc(tenant, [ACTIVA, EN_PRUEBA])}: la
 * propia consulta ya filtra por los estados que otorgan acceso. Por eso, para un
 * estado que NO otorga acceso (SUSPENDIDA/CANCELADA/VENCIDA) la consulta no
 * devuelve fila alguna; aqui se modela devolviendo una lista vacia del repo,
 * exactamente como haria la BD.</p>
 */
class PlanModulosGatingPropertyTest {

    private static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PLAN_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PAQUETE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String ACTOR = "super";

    /** "Hoy" conocido para el corte de vigencia. */
    private static final LocalDate HOY = LocalDate.of(2025, 6, 15);

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2025-06-15T00:00:00Z"), ZoneOffset.UTC);

    private static final List<EstadoSuscripcion> ESTADOS_QUE_OTORGAN =
            List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.EN_PRUEBA);

    /** Universo de modulos usado por los generadores. */
    private static final List<String> UNIVERSO_MODULOS =
            List.of("comercial", "facturacion", "inventario", "estrategia", "operacion", "contabilidad");

    // ----------------------------------------------------------------------
    // Generadores comunes
    // ----------------------------------------------------------------------

    @Provide
    Arbitrary<EstadoSuscripcion> estados() {
        return Arbitraries.of(EstadoSuscripcion.values());
    }

    @Provide
    Arbitrary<TipoInstrumento> tiposInstrumento() {
        return Arbitraries.of(TipoInstrumento.PLAN, TipoInstrumento.SUSCRIPCION);
    }

    /**
     * Desplazamiento de {@code vigenciaFin} respecto a HOY, en dias:
     * negativo = vencido (antes de hoy), {@code 0} = en el mismo dia (NO vencido),
     * positivo = futuro (NO vencido). El valor centinela {@code Integer.MIN_VALUE}
     * representa {@code vigenciaFin == null} (sin fin, concede siempre).
     */
    @Provide
    Arbitrary<Integer> offsetsVigencia() {
        Arbitrary<Integer> dias = Arbitraries.integers().between(-400, 400);
        Arbitrary<Integer> sinFin = Arbitraries.just(Integer.MIN_VALUE);
        return Arbitraries.oneOf(dias, sinFin);
    }

    /** Subconjuntos (posiblemente vacios) del universo de modulos. */
    @Provide
    Arbitrary<Set<String>> conjuntosDeModulos() {
        return Arbitraries.of(UNIVERSO_MODULOS).set().ofMaxSize(UNIVERSO_MODULOS.size());
    }

    // ----------------------------------------------------------------------
    // Utilidades de construccion (mismo patron que las pruebas por ejemplo)
    // ----------------------------------------------------------------------

    private PlanModulosPlanAdapter nuevoAdapter(SuscripcionRepository suscripcionRepository,
                                                PlanRepository planRepository,
                                                PaqueteSuscripcionRepository paqueteSuscripcionRepository) {
        TenantSessionInitializer tenantSession = mock(TenantSessionInitializer.class);
        return new PlanModulosPlanAdapter(
                suscripcionRepository, planRepository, paqueteSuscripcionRepository, tenantSession, CLOCK);
    }

    /**
     * Inicio de vigencia suficientemente antiguo para preceder a CUALQUIER
     * vigenciaFin generada (el offset mas negativo es -400). Asi el rango de
     * vigencia siempre es valido y la unica variable de interes es si
     * vigenciaFin queda antes/igual/despues de HOY.
     */
    private static final LocalDate INICIO = HOY.minusDays(500);

    /** Construye un Contrato que otorga acceso (ACTIVA o EN_PRUEBA) del tipo dado, con la vigenciaFin dada. */
    private Suscripcion contratoQueOtorga(EstadoSuscripcion estado, TipoInstrumento tipo, LocalDate vigenciaFin) {
        Suscripcion contrato;
        if (tipo == TipoInstrumento.PLAN) {
            contrato = Suscripcion.crearDePlan(TENANT, PLAN_ID, INICIO, null, ACTOR);
        } else {
            contrato = Suscripcion.crearDeSuscripcion(TENANT, PAQUETE_ID, INICIO, null, ACTOR);
        }
        // Ajusta el estado que otorga acceso.
        if (estado == EstadoSuscripcion.EN_PRUEBA) {
            // Un contrato de suscripcion en prueba: solo aplica a instrumento SUSCRIPCION.
            // Para PLAN mantenemos ACTIVA (unico estado que otorga acceso para PLAN).
            if (tipo == TipoInstrumento.SUSCRIPCION) {
                contrato = Suscripcion.crearEnPrueba(TENANT, PAQUETE_ID, INICIO, 6, ACTOR);
            }
        }
        // Fija la vigenciaFin exacta (actualizarVigencia solo valida el rango).
        contrato.actualizarVigencia(INICIO, vigenciaFin, ACTOR);
        return contrato;
    }

    /** Traduce un offset a la vigenciaFin correspondiente (centinela = null). */
    private LocalDate vigenciaFinDe(int offset) {
        return (offset == Integer.MIN_VALUE) ? null : HOY.plusDays(offset);
    }

    private boolean concedeAcceso(int offset) {
        // Sin fin => concede; en el mismo dia (offset 0) => concede (corte estricto);
        // futuro (>0) => concede; pasado (<0) => vencido, deniega.
        if (offset == Integer.MIN_VALUE) {
            return true;
        }
        return offset >= 0;
    }

    private Plan planConModulos(Set<String> modulos) {
        return PlanTestFactory.conModulos("Plan", 10, modulos);
    }

    private PaqueteSuscripcion paqueteConModulos(Set<String> modulos) {
        Map<String, BigDecimal> precios = new LinkedHashMap<>();
        for (String m : modulos) {
            precios.put(m, new BigDecimal("0.00"));
        }
        return PaqueteSuscripcion.crear(
                "Paq", 10, PlanTestFactory.GIRO_ID, "MXN", precios, 300, false, null, ACTOR);
    }

    private static Set<String> normalizar(Set<String> modulos) {
        return modulos.stream()
                .map(m -> m.strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    // ----------------------------------------------------------------------
    // Property 5: El gating concede acceso solo con estado y vigencia validos
    // Validates: Requirements 5.6, 6.1, 6.2, 6.3, 6.4
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 5: El gating concede acceso solo con estado y vigencia validos
    @Property(tries = 300)
    void elGatingConcedeSoloConEstadoYVigenciaValidos(
            @ForAll("estados") EstadoSuscripcion estado,
            @ForAll("tiposInstrumento") TipoInstrumento tipo,
            @ForAll("offsetsVigencia") int offset,
            @ForAll("conjuntosDeModulos") Set<String> modulosInstrumento) {

        SuscripcionRepository suscripcionRepository = mock(SuscripcionRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        PaqueteSuscripcionRepository paqueteSuscripcionRepository = mock(PaqueteSuscripcionRepository.class);
        PlanModulosPlanAdapter adapter =
                nuevoAdapter(suscripcionRepository, planRepository, paqueteSuscripcionRepository);

        boolean estadoOtorga = ESTADOS_QUE_OTORGAN.contains(estado);
        LocalDate vigenciaFin = vigenciaFinDe(offset);

        if (estadoOtorga) {
            Suscripcion contrato = contratoQueOtorga(estado, tipo, vigenciaFin);
            when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(TENANT, ESTADOS_QUE_OTORGAN))
                    .thenReturn(List.of(contrato));
            if (tipo == TipoInstrumento.PLAN) {
                when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(planConModulos(modulosInstrumento)));
            } else {
                when(paqueteSuscripcionRepository.findById(PAQUETE_ID))
                        .thenReturn(Optional.of(paqueteConModulos(modulosInstrumento)));
            }
        } else {
            // Estado que NO otorga acceso (SUSPENDIDA/CANCELADA/VENCIDA): la consulta
            // filtrada por [ACTIVA, EN_PRUEBA] no devuelve fila alguna.
            when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(TENANT, ESTADOS_QUE_OTORGAN))
                    .thenReturn(List.of());
        }

        boolean deberiaConceder = estadoOtorga && concedeAcceso(offset);
        List<String> efectivos = adapter.modulosHabilitadosDe(TENANT);

        if (deberiaConceder) {
            assertThat(efectivos)
                    .as("estado=%s tipo=%s offset=%d debe conceder los modulos del instrumento", estado, tipo, offset)
                    .containsExactlyInAnyOrderElementsOf(normalizar(modulosInstrumento));
        } else {
            assertThat(efectivos)
                    .as("estado=%s tipo=%s offset=%d NO debe conceder modulos (cero)", estado, tipo, offset)
                    .isEmpty();
        }
    }

    // Feature: plan-vs-suscripcion-contratacion, Property 5: El gating concede acceso solo con estado y vigencia validos
    @Property(tries = 100)
    void sinContratoVigenteNoConcedeModulos(
            @ForAll("conjuntosDeModulos") Set<String> modulosInstrumento) {
        SuscripcionRepository suscripcionRepository = mock(SuscripcionRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        PaqueteSuscripcionRepository paqueteSuscripcionRepository = mock(PaqueteSuscripcionRepository.class);
        PlanModulosPlanAdapter adapter =
                nuevoAdapter(suscripcionRepository, planRepository, paqueteSuscripcionRepository);

        // No hay Contrato en un estado que otorgue acceso.
        when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(TENANT, ESTADOS_QUE_OTORGAN))
                .thenReturn(List.of());

        assertThat(adapter.modulosHabilitadosDe(TENANT)).isEmpty();
    }

    // ----------------------------------------------------------------------
    // Property 6: Resolucion de modulos segun instrumento (override manda)
    // Validates: Requirements 6.1, 12.6
    // ----------------------------------------------------------------------

    // Feature: plan-vs-suscripcion-contratacion, Property 6: Resolucion de modulos segun instrumento
    @Property(tries = 300)
    void resolucionDeModulosSegunInstrumentoConOverridePrecedente(
            @ForAll("tiposInstrumento") TipoInstrumento tipo,
            @ForAll("conjuntosDeModulos") Set<String> modulosInstrumento,
            @ForAll boolean conOverride,
            @ForAll("conjuntosDeModulos") Set<String> modulosOverride) {

        SuscripcionRepository suscripcionRepository = mock(SuscripcionRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        PaqueteSuscripcionRepository paqueteSuscripcionRepository = mock(PaqueteSuscripcionRepository.class);
        PlanModulosPlanAdapter adapter =
                nuevoAdapter(suscripcionRepository, planRepository, paqueteSuscripcionRepository);

        // Contrato que otorga acceso (ACTIVA para PLAN; ACTIVA para SUSCRIPCION),
        // sin fin de vigencia (concede siempre) para aislar la resolucion de modulos.
        Suscripcion contrato = contratoQueOtorga(EstadoSuscripcion.ACTIVA, tipo, null);
        if (conOverride) {
            contrato.asignarModulos(modulosOverride, ACTOR);
        }
        when(suscripcionRepository.findByTenantIdAndEstadoInOrderByIdAsc(TENANT, ESTADOS_QUE_OTORGAN))
                .thenReturn(List.of(contrato));
        // Se stubbea el instrumento siempre; con override presente el adaptador no lo consulta.
        if (tipo == TipoInstrumento.PLAN) {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(planConModulos(modulosInstrumento)));
        } else {
            when(paqueteSuscripcionRepository.findById(PAQUETE_ID))
                    .thenReturn(Optional.of(paqueteConModulos(modulosInstrumento)));
        }

        List<String> efectivos = adapter.modulosHabilitadosDe(TENANT);

        Set<String> esperados = conOverride ? normalizar(modulosOverride) : normalizar(modulosInstrumento);
        assertThat(efectivos)
                .as("tipo=%s conOverride=%s debe resolver los modulos de %s",
                        tipo, conOverride, conOverride ? "el override" : "el instrumento")
                .containsExactlyInAnyOrderElementsOf(esperados);
    }
}
