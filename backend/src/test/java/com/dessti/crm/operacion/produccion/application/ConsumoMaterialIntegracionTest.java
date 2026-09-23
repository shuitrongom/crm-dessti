package com.dessti.crm.operacion.produccion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;
import com.dessti.crm.operacion.inventario.adapter.out.persistence.MovimientoInventarioRepository;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterialPort;
import com.dessti.crm.operacion.inventario.application.NotificadorStockPort;
import com.dessti.crm.operacion.inventario.application.ServicioInventario;
import com.dessti.crm.operacion.inventario.domain.Material;
import com.dessti.crm.operacion.inventario.domain.MovimientoInventario;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.PartidaOrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.PartidaOrdenFabricacion;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.operacion.produccion.application.PruebaDisenoAprobadaPort;

/**
 * Prueba de integracion del <strong>cableado real</strong> del consumo de Materiales
 * (Validates: Requirements 6.1, 6.2, 6.3, 6.7, 14.3) — tarea 3.6 del spec
 * operacion-produccion-enterprise.
 *
 * <p>A diferencia de la Property 3 ({@code ConsumoAtomicoPropertyTest}), que emula el
 * inventario con un <em>fake</em> in-memory del {@link ConsumoMaterialPort}, esta
 * prueba cablea el {@link ServicioInventario} <strong>real</strong> como implementacion
 * del puerto y lo inyecta en un {@link ServicioOrdenesFabricacion} real, de modo que se
 * ejercita el camino de codigo autentico de ambos servicios en la transicion
 * {@code pendiente -> en_produccion} de
 * {@link ServicioOrdenesFabricacion#cambiarEstado(UUID, String)}.</p>
 *
 * <h2>Decision de alcance: IT de "unidad de aplicacion" sin Docker</h2>
 * <p>El cableado extremo-a-extremo con Spring/PostgreSQL real (Testcontainers) exige
 * Docker y no es ejecutable en este entorno; ese arranque real se valida en la tarea 9.
 * Sin embargo, {@link ServicioInventario} <em>no</em> es inseparable de la base de
 * datos: su logica de consumo (no-negatividad, atomicidad, una salida por partida,
 * auditoria) reside en la capa de aplicacion y en el dominio
 * ({@code Material.aplicarMovimiento}), y solo depende de dos repositorios Spring Data y
 * de dos puertos. Por eso se lo puede cablear <em>real</em> respaldando sus repositorios
 * con estado en memoria (mocks de Mockito sobre {@link Map}/{@link List} que guardan
 * entidades {@code Material} reales y los {@code MovimientoInventario} persistidos). Asi
 * se ejercita el {@code ServicioInventario} genuino — no un fake — sin necesidad de
 * Docker.</p>
 *
 * <h2>Semantica transaccional emulada</h2>
 * <p>En produccion, ambos servicios comparten una unica {@code @Transactional}; ante un
 * fallo (422/404) el rollback revierte tanto los movimientos como el cambio de estado.
 * Fuera de Spring no hay transaccion real, pero {@code ServicioInventario} valida cada
 * salida <em>antes</em> de mutar (via {@code Material.aplicarMovimiento}, que lanza
 * <em>antes</em> de tocar existencias) y {@code ServicioOrdenesFabricacion} invoca el
 * consumo <strong>antes</strong> de {@code orden.cambiarEstado(...)}. Por tanto, cuando
 * el consumo falla, ni se registran los movimientos de las lineas restantes, ni cambia
 * el estado de la OF: el resultado observable coincide con el rollback real (todo-o-nada
 * a nivel de la operacion completa cuando la primera linea que no cabe aborta el flujo).
 * Para que el caso "insuficiente" sea inequivoco, se usa una <strong>unica</strong>
 * partida cuyo Material no tiene existencias suficientes, de modo que el rechazo ocurre
 * en la primera (y unica) salida, sin movimientos previos.</p>
 */
@DisplayName("Integracion: cableado real de ServicioInventario como ConsumoMaterialPort (tarea 3.6)")
class ConsumoMaterialIntegracionTest {

    /** Escala coherente con NUMERIC(18,4) de las partidas y NUMERIC(18,3) del Material. */
    private static final int ESCALA = 4;

    @AfterEach
    void limpiarContexto() {
        // El servicio audita via TenantContext.require() (thread-local); se limpia
        // tras cada prueba para no filtrar estado entre casos.
        TenantContext.clear();
    }

    @Test
    @DisplayName("Existencias suficientes: salida por partida y OF en en_produccion (Req 6.1, 6.7)")
    void existenciasSuficientes_registraSalidasYTransitaAEnProduccion() {
        TenantContext.set(UUID.randomUUID());

        InventarioEnMemoria inventario = new InventarioEnMemoria();
        // Dos Materiales con existencias holgadas para dos partidas.
        Material acero = inventario.altaMaterial("Acero", "kg", 50);
        Material vinil = inventario.altaMaterial("Vinil", "m2", 30);

        // ServicioInventario REAL cableado sobre repositorios en memoria.
        ServicioInventario servicioInventario = inventario.servicioInventarioReal();

        // OF en 'pendiente' (origen generico) con dos partidas que caben.
        OrdenFabricacion orden = OrdenFabricacion.crearDirecta(UUID.randomUUID(), "tester");
        List<PartidaOrdenFabricacion> partidas = List.of(
                PartidaOrdenFabricacion.crear(orden.getId(), acero.getId(), escala(10), "tester"),
                PartidaOrdenFabricacion.crear(orden.getId(), vinil.getId(), escala(5), "tester"));

        ServicioOrdenesFabricacion servicioOf =
                servicioOrdenesConInventarioReal(orden, partidas, servicioInventario);

        OrdenFabricacionDto dto = servicioOf.cambiarEstado(orden.getId(), "en_produccion");

        // La OF quedo en en_produccion (Req 6.7).
        assertThat(dto.estado()).isEqualTo("en_produccion");
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenFabricacion.EN_PRODUCCION);

        // Se registro exactamente una salida por partida, atribuida a la OF (Req 6.1).
        List<MovimientoInventario> movimientos = inventario.movimientos();
        assertThat(movimientos).hasSize(2);
        assertThat(movimientos).allSatisfy(mov -> {
            assertThat(mov.getTipo().valorBd()).isEqualTo("salida");
            assertThat(mov.getOrdenFabricacionId()).isEqualTo(orden.getId());
            assertThat(mov.getExistenciasResultantes()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });

        // Existencias descontadas por el ServicioInventario real: 50-10 y 30-5.
        assertThat(inventario.existencias(acero.getId())).isEqualByComparingTo(escala(40));
        assertThat(inventario.existencias(vinil.getId())).isEqualByComparingTo(escala(25));
    }

    @Test
    @DisplayName("Existencias insuficientes: 422 sin efectos y OF en pendiente (Req 6.2, 6.3)")
    void existenciasInsuficientes_rechaza422SinEfectosYOfPermaneceEnPendiente() {
        TenantContext.set(UUID.randomUUID());

        InventarioEnMemoria inventario = new InventarioEnMemoria();
        // Un Material con existencias insuficientes para la partida (tiene 3, se piden 10).
        Material acero = inventario.altaMaterial("Acero", "kg", 3);

        ServicioInventario servicioInventario = inventario.servicioInventarioReal();

        OrdenFabricacion orden = OrdenFabricacion.crearDirecta(UUID.randomUUID(), "tester");
        List<PartidaOrdenFabricacion> partidas = List.of(
                PartidaOrdenFabricacion.crear(orden.getId(), acero.getId(), escala(10), "tester"));

        ServicioOrdenesFabricacion servicioOf =
                servicioOrdenesConInventarioReal(orden, partidas, servicioInventario);

        // El consumo real rechaza con 422 "existencias insuficientes" (Req 6.3).
        assertThatThrownBy(() -> servicioOf.cambiarEstado(orden.getId(), "en_produccion"))
                .isInstanceOf(ReglaNegocioException.class);

        // Sin efectos: ningun movimiento persistido (Req 6.2).
        assertThat(inventario.movimientos()).isEmpty();
        // Existencias intactas.
        assertThat(inventario.existencias(acero.getId())).isEqualByComparingTo(escala(3));
        // La OF permanece en pendiente (Req 6.2, 6.3).
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenFabricacion.PENDIENTE);
    }

    // ------------------------------------------------------------------
    // Cableado de ServicioOrdenesFabricacion con el ServicioInventario real
    // ------------------------------------------------------------------

    /**
     * Arma un {@link ServicioOrdenesFabricacion} real cuyos repositorios se respaldan con
     * el estado de la OF bajo prueba y cuyo {@link ConsumoMaterialPort} es el
     * {@code servicioInventario} REAL recibido. Los demas colaboradores no participan en
     * {@code cambiarEstado} y se dejan como mocks neutrales.
     */
    private static ServicioOrdenesFabricacion servicioOrdenesConInventarioReal(
            OrdenFabricacion orden,
            List<PartidaOrdenFabricacion> partidas,
            ServicioInventario servicioInventario) {

        OrdenFabricacionRepository ordenRepository = mock(OrdenFabricacionRepository.class);
        PartidaOrdenFabricacionRepository partidaRepository =
                mock(PartidaOrdenFabricacionRepository.class);
        lenient().when(ordenRepository.findById(orden.getId()))
                .thenReturn(Optional.of(orden));
        lenient().when(ordenRepository.save(any(OrdenFabricacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(partidaRepository.findByOrdenFabricacionId(orden.getId()))
                .thenReturn(partidas);

        CotizacionParaFabricacionPort cotizacionParaFabricacion =
                mock(CotizacionParaFabricacionPort.class);
        PruebaDisenoAprobadaPort pruebaDisenoAprobada = mock(PruebaDisenoAprobadaPort.class);
        ClienteExistentePort clienteExistente = mock(ClienteExistentePort.class);
        MaterialAccesiblePort materialAccesible = mock(MaterialAccesiblePort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        return new ServicioOrdenesFabricacion(
                ordenRepository, partidaRepository, cotizacionParaFabricacion,
                pruebaDisenoAprobada, clienteExistente, materialAccesible,
                servicioInventario, auditoria);
    }

    // ------------------------------------------------------------------
    // Inventario respaldado en memoria para el ServicioInventario REAL
    // ------------------------------------------------------------------

    /**
     * Estado de inventario en memoria que respalda al {@link ServicioInventario} real:
     * conserva entidades {@link Material} genuinas en un {@link Map} y los
     * {@link MovimientoInventario} persistidos en una {@link List}. Los repositorios se
     * exponen como mocks de Mockito cuyas respuestas leen/escriben este estado, de forma
     * que {@code ServicioInventario} ejecuta su camino de codigo real
     * ({@code Material.aplicarMovimiento}, snapshot de existencias, alta de movimiento y
     * auditoria) sin base de datos.
     */
    private static final class InventarioEnMemoria {

        private final Map<UUID, Material> materiales = new HashMap<>();
        private final List<MovimientoInventario> movimientos = new ArrayList<>();

        /** Da de alta un Material real y le fija existencias iniciales via una entrada. */
        Material altaMaterial(String nombre, String unidad, int existenciasIniciales) {
            Material material = Material.crear(nombre, unidad, escala(0), "tester");
            materiales.put(material.getId(), material);
            if (existenciasIniciales > 0) {
                // Se usa el propio motor de dominio para dejar el saldo inicial (entrada).
                material.aplicarMovimiento(
                        com.dessti.crm.operacion.inventario.domain.TipoMovimientoInventario.ENTRADA,
                        escala(existenciasIniciales), "tester");
            }
            return material;
        }

        /** Construye el {@link ServicioInventario} REAL cableado a este estado. */
        ServicioInventario servicioInventarioReal() {
            MaterialRepository materialRepository = mock(MaterialRepository.class);
            MovimientoInventarioRepository movimientoRepository =
                    mock(MovimientoInventarioRepository.class);
            NotificadorStockPort notificadorStock = mock(NotificadorStockPort.class);
            AuditoriaPort auditoria = mock(AuditoriaPort.class);

            lenient().when(materialRepository.findByIdAndActivoTrue(any(UUID.class)))
                    .thenAnswer(inv -> Optional.ofNullable(materiales.get(inv.<UUID>getArgument(0))));
            // save del Material: el saldo ya fue mutado por el dominio; se conserva la
            // misma instancia en el mapa (idempotente) y se devuelve.
            lenient().when(materialRepository.save(any(Material.class)))
                    .thenAnswer(inv -> {
                        Material m = inv.getArgument(0);
                        materiales.put(m.getId(), m);
                        return m;
                    });
            // save del Movimiento_Inventario: se agrega al historial en memoria.
            lenient().when(movimientoRepository.save(any(MovimientoInventario.class)))
                    .thenAnswer(inv -> {
                        MovimientoInventario mov = inv.getArgument(0);
                        movimientos.add(mov);
                        return mov;
                    });

            return new ServicioInventario(
                    materialRepository, movimientoRepository, notificadorStock, auditoria);
        }

        BigDecimal existencias(UUID materialId) {
            return materiales.get(materialId).getExistencias();
        }

        List<MovimientoInventario> movimientos() {
            return List.copyOf(movimientos);
        }
    }

    private static BigDecimal escala(int valor) {
        return BigDecimal.valueOf(valor).setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
