package com.dessti.crm.operacion.produccion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterialPort;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.PartidaOrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.operacion.produccion.application.PruebaDisenoAprobadaPort;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 4: las partidas/BOM
 * solo se editan mientras la OF esta en {@code pendiente}</strong> (Req 5.1, 5.2).
 *
 * <p>Enunciado (para toda Orden_Fabricacion y todo estado): agregar/reemplazar/
 * eliminar partidas es aceptado <strong>si y solo si</strong> el estado de la OF es
 * {@code pendiente}; en cualquier otro estado la edicion se rechaza con
 * <strong>422</strong> ({@link ReglaNegocioException}) con el mensaje
 * "las partidas solo se editan con la Orden_Fabricacion en pendiente" y las partidas
 * persistidas quedan sin cambios (no se borra ni se inserta ninguna).</p>
 *
 * <p>El servicio real expone la edicion de partidas via
 * {@link ServicioOrdenesFabricacion#reemplazarPartidas(UUID, List)} (reemplazo total
 * del conjunto de partidas; el "agregar" y el "eliminar" se modelan como reemplazos
 * del conjunto completo, §B1). La guarda de estado se aplica <em>antes</em> de tocar
 * el repositorio de partidas, por lo que verificamos la ausencia de escritura
 * ({@code deleteByOrdenFabricacionId}/{@code save}) cuando la OF no esta en
 * {@code pendiente}.</p>
 *
 * <p>Se aisla la unica dimension bajo prueba —el estado de la OF— inyectando dobles
 * de Mockito para todos los puertos/repositorios del constructor de 8 argumentos. No
 * hay contexto de Spring, ni base de datos, ni Testcontainers.</p>
 *
 * <p><strong>Aislamiento entre intentos:</strong> el servicio audita via
 * {@link TenantContext#require()} (thread-local) y jqwik reutiliza el hilo entre
 * intentos; {@link #limpiarContexto()} limpia el {@link TenantContext} tras cada
 * intento para evitar fugas de estado.</p>
 */
@Label("Feature: operacion-produccion-enterprise, Property 4: agregar/reemplazar/eliminar aceptado "
        + "sii estado == pendiente; en otro estado 422 y partidas sin cambios")
class PartidasEdicionPropertyTest {

    private static final String MENSAJE_NO_PENDIENTE =
            "las partidas solo se editan con la Orden_Fabricacion en pendiente";

    @AfterTry
    void limpiarContexto() {
        TenantContext.clear();
    }

    @Property(tries = 200)
    void partidasSoloEditablesEnPendiente(@ForAll("estados") EstadoOrdenFabricacion estado) {
        // El tenant debe estar en contexto: el servicio audita via TenantContext.require().
        TenantContext.set(UUID.randomUUID());

        OrdenFabricacionRepository ordenRepository = mock(OrdenFabricacionRepository.class);
        PartidaOrdenFabricacionRepository partidaRepository =
                mock(PartidaOrdenFabricacionRepository.class);
        CotizacionParaFabricacionPort cotizacionParaFabricacion =
                mock(CotizacionParaFabricacionPort.class);
        PruebaDisenoAprobadaPort pruebaDisenoAprobada = mock(PruebaDisenoAprobadaPort.class);
        ClienteExistentePort clienteExistente = mock(ClienteExistentePort.class);
        MaterialAccesiblePort materialAccesible = mock(MaterialAccesiblePort.class);
        ConsumoMaterialPort consumoMaterialPort = mock(ConsumoMaterialPort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        // OF en el estado bajo prueba, devuelta por findById(...) (la unica ruta de carga).
        OrdenFabricacion orden = ordenEnEstado(estado);
        UUID ordenId = orden.getId();
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));

        // save(...) devuelve la misma entidad recibida (materializa el camino feliz).
        lenient().when(ordenRepository.save(any(OrdenFabricacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Camino feliz con lista de partidas vacia: no se consulta materialAccesible; aun
        // asi se deja como accesible por robustez ante cualquier ruta.
        lenient().when(materialAccesible.esAccesible(any(UUID.class))).thenReturn(true);
        lenient().when(partidaRepository.findByOrdenFabricacionId(any(UUID.class)))
                .thenReturn(List.of());

        ServicioOrdenesFabricacion servicio = new ServicioOrdenesFabricacion(
                ordenRepository, partidaRepository, cotizacionParaFabricacion,
                pruebaDisenoAprobada, clienteExistente, materialAccesible,
                consumoMaterialPort, auditoria);

        // Lista de partidas vacia: aisla la guarda de estado del camino de validacion de
        // cantidades/Materiales (que pertenece a otras propiedades/tareas).
        List<CrearOrdenDirectaCommand.PartidaInicial> nuevasPartidas = List.of();

        if (estado == EstadoOrdenFabricacion.PENDIENTE) {
            // En 'pendiente' la edicion es aceptada: se borran las previas y se
            // (re)insertan las nuevas (aqui, ninguna), y se devuelve el detalle.
            OrdenFabricacionDetalleDto dto = servicio.reemplazarPartidas(ordenId, nuevasPartidas);
            assertThat(dto).as("el reemplazo en 'pendiente' devuelve el detalle").isNotNull();
            verify(partidaRepository).deleteByOrdenFabricacionId(ordenId);
        } else {
            // En cualquier otro estado: 422 con el mensaje exacto y SIN tocar las
            // partidas persistidas (ni borrar ni insertar) -> quedan sin cambios.
            assertThatThrownBy(() -> servicio.reemplazarPartidas(ordenId, nuevasPartidas))
                    .as("editar partidas fuera de 'pendiente' debe rechazarse con 422")
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(MENSAJE_NO_PENDIENTE);
            verify(partidaRepository, never()).deleteByOrdenFabricacionId(any(UUID.class));
            verify(partidaRepository, never()).save(any());
            // La OF conserva su estado original (la guarda no lo altera).
            assertThat(orden.getEstado())
                    .as("una edicion rechazada no altera el estado de la OF")
                    .isEqualTo(estado);
        }
    }

    // ----------------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------------

    /**
     * Construye una {@link OrdenFabricacion} en el estado indicado. Las factorias de
     * dominio solo crean en {@code pendiente}; los demas estados se alcanzan via la
     * maquina de estados pura ({@code cambiarEstado}), respetando las transiciones
     * validas (Req 7.5).
     */
    private static OrdenFabricacion ordenEnEstado(EstadoOrdenFabricacion estado) {
        OrdenFabricacion orden = OrdenFabricacion.crearDirecta(UUID.randomUUID(), "tester");
        switch (estado) {
            case PENDIENTE -> {
                // ya esta en pendiente
            }
            case EN_PRODUCCION -> orden.cambiarEstado(EstadoOrdenFabricacion.EN_PRODUCCION, "tester");
            case TERMINADA -> {
                orden.cambiarEstado(EstadoOrdenFabricacion.EN_PRODUCCION, "tester");
                orden.cambiarEstado(EstadoOrdenFabricacion.TERMINADA, "tester");
            }
            case CANCELADA -> orden.cambiarEstado(EstadoOrdenFabricacion.CANCELADA, "tester");
        }
        return orden;
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Estado aleatorio de la OF entre los cuatro posibles (Req 7.5). */
    @Provide
    Arbitrary<EstadoOrdenFabricacion> estados() {
        return Arbitraries.of(EstadoOrdenFabricacion.values());
    }
}
