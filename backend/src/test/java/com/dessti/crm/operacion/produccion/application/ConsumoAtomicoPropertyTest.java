package com.dessti.crm.operacion.produccion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.dessti.crm.operacion.inventario.application.ConsumoMaterial;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterialPort;
import com.dessti.crm.operacion.inventario.application.MovimientoInventarioDto;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.PartidaOrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.PartidaOrdenFabricacion;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.operacion.produccion.application.PruebaDisenoAprobadaPort;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Prueba basada en propiedades (jqwik) de la <strong>Property 3: consumo de
 * materiales atomico y no-negativo</strong> (Validates: Requirements 6.1, 6.2, 6.3,
 * 6.5, 6.6) del diseño de operacion-produccion-enterprise, sobre
 * {@link ServicioOrdenesFabricacion#cambiarEstado(UUID, String)} en la transicion
 * {@code pendiente -> en_produccion}.
 *
 * <p>Enunciado (para toda OF en {@code pendiente} con un conjunto de partidas y todo
 * estado de existencias por Material):</p>
 * <ul>
 *   <li>si algun consumo dejaria las existencias de algun Material por debajo de
 *       cero, entonces <strong>ningun</strong> {@code Movimiento_Inventario} se
 *       persiste (todo-o-nada), la transicion se rechaza con
 *       {@link ReglaNegocioException} (422) y la OF <strong>permanece en
 *       {@code pendiente}</strong> (Req 6.2, 6.3, 6.5);</li>
 *   <li>si todos los consumos caben, se registra <strong>exactamente una salida por
 *       partida</strong>, las existencias resultantes de cada Material son
 *       <strong>&ge; 0</strong> y la OF pasa a {@code en_produccion} (Req 6.1, 6.6);</li>
 *   <li>una OF <strong>sin partidas</strong> transita a {@code en_produccion} sin
 *       generar ningun movimiento (Req 6.6).</li>
 * </ul>
 *
 * <p>El mecanismo real de consumo vive en {@code ServicioInventario}
 * ({@link ConsumoMaterialPort}); para mantener el costo bajo se usa un
 * <em>fake</em> in-memory ({@link ConsumoMaterialPortEnMemoria}) que emula su
 * comportamiento atomico y no-negativo: valida todas las lineas antes de mutar y,
 * si alguna no cabe, no persiste ningun movimiento (rollback logico). Los
 * repositorios se inyectan como <em>mocks</em> de Mockito respaldados por el estado
 * de la OF bajo prueba; no hay contexto de Spring ni base de datos.</p>
 *
 * <p><strong>Aislamiento entre intentos:</strong> el servicio audita via
 * {@link TenantContext#require()} (thread-local) y jqwik reutiliza el hilo entre
 * intentos; {@link #limpiarContexto()} limpia el {@link TenantContext} tras cada
 * intento.</p>
 */
@Label("Feature: operacion-produccion-enterprise, Property 3: si algun consumo dejaria existencias < 0, "
        + "ningun Movimiento_Inventario se persiste y la OF permanece en pendiente (422); si todos caben, "
        + "un salida por partida, existencias resultantes >= 0 y OF en en_produccion; sin partidas transita "
        + "sin movimientos")
class ConsumoAtomicoPropertyTest {

    /** Escala decimal coherente con NUMERIC(18,4) de las partidas/existencias. */
    private static final int ESCALA = 4;

    @AfterTry
    void limpiarContexto() {
        TenantContext.clear();
    }

    // ======================================================================
    // Property 3
    // ======================================================================

    @Property(tries = 200)
    void consumoAtomicoYNoNegativoEnTransicionAEnProduccion(
            @ForAll("escenarios") Escenario escenario) {

        TenantContext.set(UUID.randomUUID());

        // --- Fake de inventario con existencias iniciales y semantica todo-o-nada.
        ConsumoMaterialPortEnMemoria inventario =
                new ConsumoMaterialPortEnMemoria(escenario.existenciasIniciales());

        // --- OF en 'pendiente' (origen generico) sobre la que se opera.
        OrdenFabricacion orden = OrdenFabricacion.crearDirecta(UUID.randomUUID(), "tester");
        UUID ordenId = orden.getId();

        // --- Partidas de la OF (0..N), materializadas como entidades de dominio.
        List<PartidaOrdenFabricacion> partidas = new ArrayList<>();
        for (Escenario.Linea linea : escenario.partidas()) {
            partidas.add(PartidaOrdenFabricacion.crear(
                    ordenId, linea.materialId(), linea.cantidad(), "tester"));
        }

        // --- Repositorios como mocks respaldados por el estado real de la OF.
        OrdenFabricacionRepository ordenRepository = mock(OrdenFabricacionRepository.class);
        PartidaOrdenFabricacionRepository partidaRepository =
                mock(PartidaOrdenFabricacionRepository.class);
        lenient().when(ordenRepository.findById(ordenId))
                .thenReturn(java.util.Optional.of(orden));
        lenient().when(ordenRepository.save(any(OrdenFabricacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(partidaRepository.findByOrdenFabricacionId(ordenId))
                .thenReturn(partidas);

        // --- Colaboradores no ejercitados por cambiarEstado: mocks neutrales.
        CotizacionParaFabricacionPort cotizacionParaFabricacion =
                mock(CotizacionParaFabricacionPort.class);
        PruebaDisenoAprobadaPort pruebaDisenoAprobada = mock(PruebaDisenoAprobadaPort.class);
        com.dessti.crm.operacion.cliente.application.ClienteExistentePort clienteExistente =
                mock(com.dessti.crm.operacion.cliente.application.ClienteExistentePort.class);
        MaterialAccesiblePort materialAccesible = mock(MaterialAccesiblePort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        ServicioOrdenesFabricacion servicio = new ServicioOrdenesFabricacion(
                ordenRepository, partidaRepository, cotizacionParaFabricacion,
                pruebaDisenoAprobada, clienteExistente, materialAccesible,
                inventario, auditoria);

        // Se calcula de forma independiente si TODAS las lineas caben (agrega por
        // Material, pues varias partidas pueden compartir Material en el fake).
        boolean todasCaben = todasLasLineasCaben(
                escenario.existenciasIniciales(), escenario.partidas());
        Map<UUID, BigDecimal> existenciasIniciales =
                new HashMap<>(escenario.existenciasIniciales());

        if (partidas.isEmpty()) {
            // Sin partidas -> transita a en_produccion SIN generar movimientos (Req 6.6).
            OrdenFabricacionDto dto = servicio.cambiarEstado(ordenId, "en_produccion");
            assertThat(dto.estado())
                    .as("una OF sin partidas transita a en_produccion")
                    .isEqualTo("en_produccion");
            assertThat(orden.getEstado())
                    .as("el estado de la OF queda en EN_PRODUCCION")
                    .isEqualTo(EstadoOrdenFabricacion.EN_PRODUCCION);
            assertThat(inventario.movimientosPersistidos())
                    .as("una OF sin partidas no genera ningun movimiento")
                    .isEmpty();
            return;
        }

        if (!todasCaben) {
            // Algun consumo dejaria existencias < 0 -> 422, todo-o-nada (Req 6.2, 6.3).
            assertThatThrownBy(() -> servicio.cambiarEstado(ordenId, "en_produccion"))
                    .as("un consumo que dejaria existencias negativas se rechaza con 422")
                    .isInstanceOf(ReglaNegocioException.class);

            // Ningun Movimiento_Inventario se persiste (atomicidad, Req 6.2).
            assertThat(inventario.movimientosPersistidos())
                    .as("ante existencias insuficientes NO se persiste ningun movimiento")
                    .isEmpty();
            // Las existencias no cambian (rollback logico).
            assertThat(inventario.existenciasActuales())
                    .as("ante el rechazo las existencias permanecen intactas")
                    .isEqualTo(existenciasIniciales);
            // La OF permanece en pendiente (Req 6.5).
            assertThat(orden.getEstado())
                    .as("ante el rechazo la OF permanece en PENDIENTE")
                    .isEqualTo(EstadoOrdenFabricacion.PENDIENTE);
            return;
        }

        // Todos los consumos caben -> una salida por partida, existencias >= 0 y
        // OF en en_produccion (Req 6.1, 6.6).
        OrdenFabricacionDto dto = servicio.cambiarEstado(ordenId, "en_produccion");

        assertThat(dto.estado())
                .as("con existencias suficientes la OF pasa a en_produccion")
                .isEqualTo("en_produccion");
        assertThat(orden.getEstado())
                .as("el estado de la OF queda en EN_PRODUCCION")
                .isEqualTo(EstadoOrdenFabricacion.EN_PRODUCCION);

        List<MovimientoInventarioDto> movimientos = inventario.movimientosPersistidos();
        // Exactamente un movimiento 'salida' por partida (Req 6.1).
        assertThat(movimientos)
                .as("se registra exactamente una salida por partida")
                .hasSize(partidas.size());
        assertThat(movimientos)
                .allSatisfy(mov -> {
                    assertThat(mov.tipo())
                            .as("cada movimiento de consumo es de tipo salida")
                            .isEqualTo("salida");
                    assertThat(mov.ordenFabricacionId())
                            .as("cada movimiento referencia la OF de origen")
                            .isEqualTo(ordenId);
                    // Existencias resultantes de cada Material >= 0 (Req 6.6).
                    assertThat(mov.existenciasResultantes())
                            .as("las existencias resultantes nunca son negativas")
                            .isGreaterThanOrEqualTo(BigDecimal.ZERO);
                });

        // Todas las existencias finales son >= 0 (no-negatividad global, Req 6.6).
        assertThat(inventario.existenciasActuales().values())
                .as("ninguna existencia resultante es negativa")
                .allSatisfy(saldo -> assertThat(saldo).isGreaterThanOrEqualTo(BigDecimal.ZERO));
    }

    // ----------------------------------------------------------------------
    // Calculo de referencia: ¿caben todas las lineas agregadas por Material?
    // ----------------------------------------------------------------------

    private static boolean todasLasLineasCaben(
            Map<UUID, BigDecimal> existencias, List<Escenario.Linea> lineas) {
        Map<UUID, BigDecimal> requerido = new HashMap<>();
        for (Escenario.Linea linea : lineas) {
            requerido.merge(linea.materialId(), linea.cantidad(), BigDecimal::add);
        }
        for (Map.Entry<UUID, BigDecimal> e : requerido.entrySet()) {
            BigDecimal disponible = existencias.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (disponible.compareTo(e.getValue()) < 0) {
                return false;
            }
        }
        return true;
    }

    // ----------------------------------------------------------------------
    // Fake in-memory del ConsumoMaterialPort: emula la atomicidad y la
    // no-negatividad de ServicioInventario (Material.aplicarMovimiento).
    // ----------------------------------------------------------------------

    /**
     * Implementacion in-memory de {@link ConsumoMaterialPort} con la misma semantica
     * observable que {@code ServicioInventario}: valida <em>todas</em> las lineas
     * contra las existencias antes de mutar; si alguna dejaria el saldo por debajo de
     * cero, lanza {@link ReglaNegocioException} (422) sin registrar ningun movimiento
     * (rollback logico, todo-o-nada). Si todas caben, descuenta cada Material y
     * registra un {@link MovimientoInventarioDto} {@code salida} por linea.
     */
    private static final class ConsumoMaterialPortEnMemoria implements ConsumoMaterialPort {

        private final Map<UUID, BigDecimal> existencias;
        private final List<MovimientoInventarioDto> movimientos = new ArrayList<>();

        ConsumoMaterialPortEnMemoria(Map<UUID, BigDecimal> existenciasIniciales) {
            this.existencias = new HashMap<>(existenciasIniciales);
        }

        @Override
        public List<MovimientoInventarioDto> consumirParaOrdenFabricacion(
                UUID ordenFabricacionId, List<ConsumoMaterial> consumos) {

            // Fase 1 (validacion todo-o-nada): agrega lo requerido por Material y
            // comprueba que ningun saldo quede negativo. No se muta nada aun.
            Map<UUID, BigDecimal> requerido = new LinkedHashMap<>();
            for (ConsumoMaterial consumo : consumos) {
                requerido.merge(consumo.materialId(), consumo.cantidad(), BigDecimal::add);
            }
            for (Map.Entry<UUID, BigDecimal> e : requerido.entrySet()) {
                BigDecimal disponible = existencias.getOrDefault(e.getKey(), BigDecimal.ZERO);
                if (disponible.compareTo(e.getValue()) < 0) {
                    throw new ReglaNegocioException(
                            "Existencias insuficientes para el consumo de la Orden_Fabricacion.");
                }
            }

            // Fase 2 (aplicacion): descuenta y registra una salida por linea.
            List<MovimientoInventarioDto> registrados = new ArrayList<>(consumos.size());
            for (ConsumoMaterial consumo : consumos) {
                BigDecimal saldo = existencias.getOrDefault(consumo.materialId(), BigDecimal.ZERO)
                        .subtract(consumo.cantidad());
                existencias.put(consumo.materialId(), saldo);
                MovimientoInventarioDto mov = new MovimientoInventarioDto(
                        UUID.randomUUID(),
                        consumo.materialId(),
                        "salida",
                        consumo.cantidad(),
                        saldo,
                        ordenFabricacionId,
                        "consumo OF",
                        0L,
                        Instant.now());
                registrados.add(mov);
                movimientos.add(mov);
            }
            return registrados;
        }

        List<MovimientoInventarioDto> movimientosPersistidos() {
            return List.copyOf(movimientos);
        }

        Map<UUID, BigDecimal> existenciasActuales() {
            return new HashMap<>(existencias);
        }
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Escenario de prueba: existencias iniciales por Material y las partidas
     * (Material + cantidad) de la OF. Los Materiales de las partidas se eligen de un
     * pequeño catalogo comun con las existencias, de modo que se cubran tanto los
     * casos que caben como los que exceden.
     */
    record Escenario(Map<UUID, BigDecimal> existenciasIniciales, List<Linea> partidas) {
        record Linea(UUID materialId, BigDecimal cantidad) {
        }
    }

    /**
     * Genera un escenario: un catalogo de 1..3 Materiales con existencias en
     * {@code [0, 50]} y una lista de 0..6 partidas cuyas cantidades en {@code [1, 40]}
     * se reparten entre esos Materiales. Con estos rangos jqwik produce con holgura
     * tanto casos donde todo cabe como casos donde algun Material se excede.
     */
    @Provide
    Arbitrary<Escenario> escenarios() {
        Arbitrary<List<UUID>> catalogo = Arbitraries.integers().between(1, 3)
                .map(n -> {
                    List<UUID> ids = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        ids.add(UUID.randomUUID());
                    }
                    return ids;
                });

        return catalogo.flatMap(materiales -> {
            // Existencias iniciales por Material (0..50).
            Arbitrary<Map<UUID, BigDecimal>> existencias =
                    existenciasDe(materiales);
            // Partidas: 0..6 lineas, cada una con un Material del catalogo y cantidad 1..40.
            Arbitrary<List<Escenario.Linea>> partidas =
                    lineaDe(materiales).list().ofMinSize(0).ofMaxSize(6);
            return Combinators.combine(existencias, partidas).as(Escenario::new);
        });
    }

    private Arbitrary<Map<UUID, BigDecimal>> existenciasDe(List<UUID> materiales) {
        Arbitrary<List<Integer>> cantidades = Arbitraries.integers().between(0, 50)
                .list().ofSize(materiales.size());
        return cantidades.map(vals -> {
            Map<UUID, BigDecimal> mapa = new HashMap<>();
            for (int i = 0; i < materiales.size(); i++) {
                mapa.put(materiales.get(i), escala(BigDecimal.valueOf(vals.get(i))));
            }
            return mapa;
        });
    }

    private Arbitrary<Escenario.Linea> lineaDe(List<UUID> materiales) {
        Arbitrary<UUID> material = Arbitraries.of(materiales);
        Arbitrary<Integer> cantidad = Arbitraries.integers().between(1, 40);
        return Combinators.combine(material, cantidad)
                .as((m, c) -> new Escenario.Linea(m, escala(BigDecimal.valueOf(c))));
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
