package com.dessti.crm.vertical.anuncios.instalacion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.EvidenciaInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.OrdenTrabajoInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.PendienteInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.OrdenTrabajoInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.PendienteInstalacion;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoCompletadoPort;
import com.dessti.crm.operacion.produccion.application.OrdenFabricacionTerminadaPort;
import com.dessti.crm.vertical.anuncios.permiso.application.PermisoAprobadoPort;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 5: La guarda de
 * cierre de OTI informa los pendientes no resueltos</strong> (Req 8.5; diseno &sect;C2).
 *
 * <p>La property valida la guarda de cierre de
 * {@link ServicioOrdenesTrabajoInstalacion#cambiarEstado(UUID, String)}: para toda
 * Orden_Trabajo_Instalacion en estado {@code en_curso} con un conjunto de pendientes
 * (cada uno resuelto o no), completar la orden es aceptado <em>si y solo si</em> no
 * existe ningun pendiente sin resolver; si existe al menos uno, la transicion se
 * rechaza con {@link ReglaNegocioException} (422), el estado se conserva
 * ({@code en_curso}) y el mensaje de error enumera <strong>exactamente</strong> las
 * descripciones de los pendientes no resueltos con el formato del backend
 * {@code [«desc1», «desc2»]}.</p>
 *
 * <h2>Enfoque de modelado (servicio real + repositorios en memoria)</h2>
 * <p>Se ejerce el servicio de produccion {@link ServicioOrdenesTrabajoInstalacion}
 * tal cual, respaldado por dobles de Mockito con estado real sobre {@code Map}s: el
 * {@link OrdenTrabajoInstalacionRepository} guarda/recupera la OTI por id, y el
 * {@link PendienteInstalacionRepository} implementa la consulta agregada que usa la
 * guarda ({@code findByOrdenTrabajoInstalacionIdAndResueltoFalseOrderByCreatedAtAsc})
 * filtrando por OTI y {@code resuelto = false}, ordenados por instante de alta. El
 * resto de puertos ({@link OrdenFabricacionTerminadaPort},
 * {@link LevantamientoCompletadoPort}, {@link PermisoAprobadoPort},
 * {@link AuditoriaPort}) son dobles no-op, pues la guarda de cierre no los consulta.
 * El {@link TenantContext} se fija antes de cada operacion (el servicio audita via
 * {@code TenantContext.require()}) y se limpia con {@link AfterTry}.</p>
 */
class GuardaCierreOtiPropertyTest {

    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** El TenantContext es un ThreadLocal: se limpia tras cada intento (Req 23). */
    @AfterTry
    void limpiarTenant() {
        TenantContext.clear();
    }

    // ----------------------------------------------------------------------
    // Property 5 — La guarda de cierre de OTI informa los pendientes no resueltos
    // ----------------------------------------------------------------------

    // Feature: operacion-produccion-enterprise, Property 5: completar aceptado sii no hay pendientes sin resolver; si existe ≥1, 422, estado conservado y el mensaje enumera exactamente las descripciones no resueltas
    @Property(tries = 300)
    void completarAceptadoSiiNoHayPendientesSinResolver(
            @ForAll("conjuntosDePendientes") @Size(min = 0, max = 12) List<PendienteGen> pendientes) {

        Escenario esc = nuevoEscenario();
        OrdenTrabajoInstalacion oti = esc.crearOtiEnCurso();
        // Se cargan los pendientes generados (resueltos/no) sobre la OTI en_curso.
        List<String> noResueltasEnOrden = esc.cargarPendientes(oti, pendientes);
        boolean hayNoResueltos = !noResueltasEnOrden.isEmpty();

        Throwable error = catchThrowable(
                () -> esc.servicio.cambiarEstado(oti.getId(), EstadoOrdenTrabajoInstalacion.COMPLETADA.valorBd()));

        if (!hayNoResueltos) {
            // Bicondicional (rama cierto): sin pendientes sin resolver, se completa.
            assertThat(error).as("sin pendientes sin resolver, el cierre se acepta").isNull();
            assertThat(esc.estadoActual(oti.getId())).isEqualTo(EstadoOrdenTrabajoInstalacion.COMPLETADA);
        } else {
            // Bicondicional (rama falso): con >=1 sin resolver, 422 y estado conservado.
            assertThat(error).isInstanceOf(ReglaNegocioException.class);
            assertThat(esc.estadoActual(oti.getId()))
                    .as("el estado se conserva en en_curso tras el rechazo")
                    .isEqualTo(EstadoOrdenTrabajoInstalacion.EN_CURSO);

            // El mensaje enumera EXACTAMENTE las descripciones no resueltas con el
            // formato del backend: [«desc1», «desc2»] (en el orden de alta).
            String mensaje = error.getMessage();
            String esperado = noResueltasEnOrden.stream()
                    .map(descripcion -> "«" + descripcion + "»")
                    .reduce((a, b) -> a + ", " + b)
                    .map(cuerpo -> "[" + cuerpo + "]")
                    .orElse("[]");
            assertThat(mensaje).contains(esperado);

            // Cada descripcion no resuelta aparece; ninguna resuelta aparece.
            for (String noResuelta : noResueltasEnOrden) {
                assertThat(mensaje).contains("«" + noResuelta + "»");
            }
            for (PendienteGen p : pendientes) {
                if (p.resuelto() && !noResueltasEnOrden.contains(p.descripcion())) {
                    assertThat(mensaje)
                            .as("una descripcion resuelta no debe enumerarse")
                            .doesNotContain("«" + p.descripcion() + "»");
                }
            }
        }
    }

    // Feature: operacion-produccion-enterprise, Property 5: completar aceptado sii no hay pendientes sin resolver; si existe ≥1, 422, estado conservado y el mensaje enumera exactamente las descripciones no resueltas
    @Property(tries = 200)
    void alMenosUnPendienteSinResolverSiempreRechazaConservandoElEstado(
            @ForAll("conjuntosDePendientes") @Size(min = 1, max = 12) List<PendienteGen> pendientes) {

        // Se fuerza que al menos uno quede sin resolver marcando el primero como no resuelto.
        List<PendienteGen> conNoResuelto = new ArrayList<>(pendientes);
        PendienteGen primero = conNoResuelto.get(0);
        conNoResuelto.set(0, new PendienteGen(primero.descripcion(), false));

        Escenario esc = nuevoEscenario();
        OrdenTrabajoInstalacion oti = esc.crearOtiEnCurso();
        esc.cargarPendientes(oti, conNoResuelto);

        assertThatThrownBy(
                () -> esc.servicio.cambiarEstado(oti.getId(), EstadoOrdenTrabajoInstalacion.COMPLETADA.valorBd()))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(esc.estadoActual(oti.getId())).isEqualTo(EstadoOrdenTrabajoInstalacion.EN_CURSO);
    }

    // ----------------------------------------------------------------------
    // Escenario: servicio de produccion + repositorios en memoria + dobles no-op
    // ----------------------------------------------------------------------

    private Escenario nuevoEscenario() {
        TenantContext.set(TENANT);
        RepositoriosEnMemoria repos = new RepositoriosEnMemoria();

        OrdenFabricacionTerminadaPort ofTerminada = mock(OrdenFabricacionTerminadaPort.class);
        LevantamientoCompletadoPort levantamientoCompletado = mock(LevantamientoCompletadoPort.class);
        PermisoAprobadoPort permisoAprobado = mock(PermisoAprobadoPort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        ServicioOrdenesTrabajoInstalacion servicio = new ServicioOrdenesTrabajoInstalacion(
                repos.ordenes(), repos.pendientes(), repos.evidencias(),
                ofTerminada, levantamientoCompletado, permisoAprobado, auditoria);
        return new Escenario(servicio, repos);
    }

    private static final class Escenario {

        private final ServicioOrdenesTrabajoInstalacion servicio;
        private final RepositoriosEnMemoria repos;

        Escenario(ServicioOrdenesTrabajoInstalacion servicio, RepositoriosEnMemoria repos) {
            this.servicio = servicio;
            this.repos = repos;
        }

        /** Crea y persiste una OTI directamente en estado {@code en_curso}. */
        OrdenTrabajoInstalacion crearOtiEnCurso() {
            OrdenTrabajoInstalacion oti = OrdenTrabajoInstalacion.programar(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    LocalDate.of(2025, 1, 15), "tester");
            oti.cambiarEstado(EstadoOrdenTrabajoInstalacion.EN_CURSO, "tester");
            repos.guardarOti(oti);
            return oti;
        }

        /**
         * Carga los pendientes generados sobre la OTI, respetando la marca
         * {@code resuelto}. Devuelve, en orden de alta, las descripciones de los
         * pendientes que quedan sin resolver (lo que la guarda debe enumerar).
         */
        List<String> cargarPendientes(OrdenTrabajoInstalacion oti, List<PendienteGen> generados) {
            List<String> sinResolver = new ArrayList<>();
            for (PendienteGen gen : generados) {
                PendienteInstalacion pendiente = PendienteInstalacion.paraOrden(oti, gen.descripcion(), "tester");
                if (gen.resuelto()) {
                    pendiente.resolver("tester");
                } else {
                    sinResolver.add(pendiente.getDescripcion());
                }
                repos.guardarPendiente(pendiente);
            }
            return sinResolver;
        }

        EstadoOrdenTrabajoInstalacion estadoActual(UUID otiId) {
            return repos.ordenPorId(otiId).getEstado();
        }
    }

    /**
     * Repositorios en memoria con estado real. La OTI se guarda/recupera por id; los
     * pendientes se guardan por id y la consulta agregada usada por la guarda
     * ({@code findByOrdenTrabajoInstalacionIdAndResueltoFalseOrderByCreatedAtAsc})
     * filtra por OTI y {@code resuelto = false} preservando el orden de alta.
     */
    private static final class RepositoriosEnMemoria {

        private final Map<UUID, OrdenTrabajoInstalacion> otisPorId = new HashMap<>();
        // Orden de insercion (orden de alta) preservado por una secuencia incremental.
        private final Map<UUID, PendienteInstalacion> pendientesPorId = new HashMap<>();
        private final Map<UUID, Long> secuenciaAlta = new HashMap<>();
        private long secuencia;

        private final OrdenTrabajoInstalacionRepository ordenesDoble = crearOrdenesDoble();
        private final PendienteInstalacionRepository pendientesDoble = crearPendientesDoble();
        private final EvidenciaInstalacionRepository evidenciasDoble =
                mock(EvidenciaInstalacionRepository.class);

        OrdenTrabajoInstalacionRepository ordenes() {
            return ordenesDoble;
        }

        PendienteInstalacionRepository pendientes() {
            return pendientesDoble;
        }

        EvidenciaInstalacionRepository evidencias() {
            return evidenciasDoble;
        }

        void guardarOti(OrdenTrabajoInstalacion oti) {
            otisPorId.put(oti.getId(), oti);
        }

        void guardarPendiente(PendienteInstalacion pendiente) {
            if (!pendientesPorId.containsKey(pendiente.getId())) {
                secuenciaAlta.put(pendiente.getId(), secuencia++);
            }
            pendientesPorId.put(pendiente.getId(), pendiente);
        }

        OrdenTrabajoInstalacion ordenPorId(UUID id) {
            return otisPorId.get(id);
        }

        private OrdenTrabajoInstalacionRepository crearOrdenesDoble() {
            OrdenTrabajoInstalacionRepository doble = mock(OrdenTrabajoInstalacionRepository.class);
            when(doble.save(any(OrdenTrabajoInstalacion.class))).thenAnswer(inv -> {
                OrdenTrabajoInstalacion oti = inv.getArgument(0);
                otisPorId.put(oti.getId(), oti);
                return oti;
            });
            when(doble.findById(any(UUID.class))).thenAnswer(inv ->
                    Optional.ofNullable(otisPorId.get(inv.<UUID>getArgument(0))));
            return doble;
        }

        private PendienteInstalacionRepository crearPendientesDoble() {
            PendienteInstalacionRepository doble = mock(PendienteInstalacionRepository.class);
            when(doble.save(any(PendienteInstalacion.class))).thenAnswer(inv -> {
                PendienteInstalacion pendiente = inv.getArgument(0);
                guardarPendiente(pendiente);
                return pendiente;
            });
            when(doble.findByOrdenTrabajoInstalacionIdAndResueltoFalseOrderByCreatedAtAsc(any(UUID.class)))
                    .thenAnswer(inv -> {
                        UUID otiId = inv.getArgument(0);
                        List<PendienteInstalacion> resultado = new ArrayList<>();
                        for (PendienteInstalacion p : pendientesPorId.values()) {
                            if (otiId.equals(p.getOrdenTrabajoInstalacionId()) && !p.isResuelto()) {
                                resultado.add(p);
                            }
                        }
                        resultado.sort(Comparator.comparingLong(p -> secuenciaAlta.get(p.getId())));
                        return resultado;
                    });
            return doble;
        }
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Un pendiente generado: descripcion no vacia y marca resuelto/no. */
    record PendienteGen(String descripcion, boolean resuelto) {
    }

    /**
     * Conjuntos de pendientes con descripciones <strong>distintas</strong> (para que
     * el orden de alta identifique de forma univoca cada descripcion enumerada) y una
     * bandera {@code resuelto} independiente por elemento.
     */
    @Provide
    Arbitrary<List<PendienteGen>> conjuntosDePendientes() {
        Arbitrary<String> descripciones = Arbitraries.strings()
                .alpha().numeric().withChars(' ')
                .ofMinLength(1).ofMaxLength(24)
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .injectDuplicates(0.0);
        Arbitrary<Boolean> resuelto = Arbitraries.of(true, false);
        Arbitrary<PendienteGen> pendiente =
                Combinators.combine(descripciones, resuelto).as(PendienteGen::new);
        return pendiente.list().ofMaxSize(12).uniqueElements(PendienteGen::descripcion);
    }
}
