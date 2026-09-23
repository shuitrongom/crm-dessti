package com.dessti.crm.operacion.produccion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterialPort;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.PartidaOrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.operacion.produccion.application.PruebaDisenoAprobadaPort;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 7: Precondiciones
 * para generar Orden de Fabricacion</strong> (Req 7.1, 7.2, 7.3, 15.5).
 *
 * <p>Reutiliza la pieza de produccion {@link ServicioOrdenesFabricacion} tal cual,
 * inyectando como <em>mocks</em> de Mockito sus cuatro colaboradores
 * ({@link CotizacionParaFabricacionPort}, {@link OrdenFabricacionRepository},
 * {@link PruebaDisenoAprobadaPort} y {@link AuditoriaPort}). No hay contexto de
 * Spring, ni base de datos, ni Testcontainers: cada precondicion se modela como
 * un valor generado y los stubs devuelven exactamente ese valor.</p>
 *
 * <p>El espacio completo de entradas se modela con cuatro dimensiones
 * independientes:</p>
 * <ul>
 *   <li>{@code cotizacionExiste} &isin; {true, false} &mdash; precondicion 0.</li>
 *   <li>{@code estadoCotizacion} &isin; {aprobada, borrador, enviada, rechazada}
 *       &mdash; solo {@code aprobada} supera la precondicion 1 (Req 7.1, 7.2).</li>
 *   <li>{@code yaTieneOF} &isin; {true, false} &mdash; precondicion 2 (Req 7.3).</li>
 *   <li>{@code tienePruebaAprobada} &isin; {true, false} &mdash; precondicion 3
 *       (Req 15.5).</li>
 * </ul>
 *
 * <p>La property comprueba universalmente que la generacion tiene exito
 * <em>si y solo si</em> se cumplen las cuatro precondiciones y que, cuando falla,
 * se lanza la excepcion de la <strong>primera</strong> precondicion incumplida en
 * el orden que el servicio aplica (0&rarr;1&rarr;2&rarr;3) y NO se persiste ninguna
 * Orden_Fabricacion.</p>
 *
 * <p><strong>Aislamiento entre iteraciones:</strong> el servicio audita via
 * {@link TenantContext#require()}, que lee un thread-local; jqwik reutiliza el
 * hilo entre intentos. Por ello {@link #limpiarContexto()} (anotado
 * {@link AfterTry}) limpia el {@link TenantContext} tras cada intento para evitar
 * fugas de estado.</p>
 */
class PrecondicionesOrdenFabricacionPropertyTest {

    /** Estados de Cotizacion; solo {@code aprobada} supera la precondicion 1. */
    private static final String ESTADO_APROBADA = "aprobada";

    // ----------------------------------------------------------------------
    // Aislamiento entre intentos: TenantContext es thread-local y jqwik
    // reutiliza el hilo. Establecer/limpiar el tenant en cada try.
    // ----------------------------------------------------------------------
    @AfterTry
    void limpiarContexto() {
        TenantContext.clear();
    }

    // ----------------------------------------------------------------------
    // Utilidad de montaje del servicio con sus colaboradores mockeados.
    // ----------------------------------------------------------------------

    /**
     * Monta un {@link ServicioOrdenesFabricacion} con mocks cuyos comportamientos
     * reflejan las cuatro precondiciones modeladas, y establece el tenant en
     * contexto (el servicio audita via {@link TenantContext#require()}).
     */
    private static Montaje montar(UUID cotizacionId, UUID clienteId, boolean cotizacionExiste,
                                  String estadoCotizacion, boolean yaTieneOF,
                                  boolean tienePruebaAprobada) {
        TenantContext.set(UUID.randomUUID());

        CotizacionParaFabricacionPort cotizacionPort = mock(CotizacionParaFabricacionPort.class);
        OrdenFabricacionRepository repositorio = mock(OrdenFabricacionRepository.class);
        PruebaDisenoAprobadaPort pruebaPort = mock(PruebaDisenoAprobadaPort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        // Precondicion 0: la Cotizacion existe (o no) en el tenant.
        Optional<CotizacionParaFabricacion> vista = cotizacionExiste
                ? Optional.of(new CotizacionParaFabricacion(cotizacionId, estadoCotizacion, clienteId))
                : Optional.empty();
        lenient().when(cotizacionPort.buscarParaFabricacion(eq(cotizacionId))).thenReturn(vista);

        // Precondicion 2: la Cotizacion ya tiene (o no) una OF vinculada.
        lenient().when(repositorio.existsByCotizacionId(eq(cotizacionId))).thenReturn(yaTieneOF);

        // Precondicion 3: la Cotizacion tiene (o no) una Prueba_Diseno aprobada.
        lenient().when(pruebaPort.tieneAprobadaPorCotizacion(eq(cotizacionId)))
                .thenReturn(tienePruebaAprobada);

        // saveAndFlush devuelve la misma entidad recibida (materializa la OF creada).
        lenient().when(repositorio.saveAndFlush(any(OrdenFabricacion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        // El origen generico (crearDirecta) no participa en las precondiciones de
        // la genesis desde Cotizacion; el puerto se mockea pero no se ejercita aqui.
        ClienteExistentePort clienteExistente = mock(ClienteExistentePort.class);
        PartidaOrdenFabricacionRepository partidaRepositorio =
                mock(PartidaOrdenFabricacionRepository.class);
        MaterialAccesiblePort materialAccesible = mock(MaterialAccesiblePort.class);
        ConsumoMaterialPort consumoMaterialPort = mock(ConsumoMaterialPort.class);

        ServicioOrdenesFabricacion servicio = new ServicioOrdenesFabricacion(
                repositorio, partidaRepositorio, cotizacionPort, pruebaPort,
                clienteExistente, materialAccesible, consumoMaterialPort, auditoria);
        return new Montaje(servicio, repositorio);
    }

    /** Cotizacion aprobada segun el estado generado. */
    private static boolean estaAprobada(String estado) {
        return ESTADO_APROBADA.equals(estado);
    }

    // ----------------------------------------------------------------------
    // Property 7 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 7: Para cualquier Cotizacion, la generación de una Orden_Fabricacion se permite si y solo si la Cotizacion está en estado "aprobada", no tiene ya una Orden_Fabricacion vinculada, y existe al menos una Prueba_Diseno en estado "aprobada".
    @Property(tries = 1000)
    void generacionExitosaSiiSeCumplenTodasLasPrecondiciones(
            @ForAll boolean cotizacionExiste,
            @ForAll("estadosCotizacion") String estadoCotizacion,
            @ForAll boolean yaTieneOF,
            @ForAll boolean tienePruebaAprobada) {

        UUID cotizacionId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        Montaje montaje = montar(cotizacionId, clienteId, cotizacionExiste, estadoCotizacion,
                yaTieneOF, tienePruebaAprobada);

        boolean deberiaTenerExito = cotizacionExiste
                && estaAprobada(estadoCotizacion)
                && !yaTieneOF
                && tienePruebaAprobada;

        if (deberiaTenerExito) {
            // si-y-solo-si (exito): DTO en 'pendiente' vinculado a cotizacion + cliente.
            OrdenFabricacionDto dto = montaje.servicio().generar(cotizacionId);
            assertThat(dto).as("generacion exitosa devuelve un DTO").isNotNull();
            assertThat(dto.estado())
                    .as("la OF generada inicia en estado 'pendiente'")
                    .isEqualTo("pendiente");
            assertThat(dto.cotizacionId())
                    .as("la OF queda vinculada a la Cotizacion de origen")
                    .isEqualTo(cotizacionId);
            assertThat(dto.clienteId())
                    .as("la OF denormaliza el Cliente de la Cotizacion")
                    .isEqualTo(clienteId);
            assertThat(dto.id()).as("la OF generada tiene identificador").isNotNull();
            // En exito, se persiste exactamente una OF.
            verify(montaje.repositorio()).saveAndFlush(any(OrdenFabricacion.class));
        } else {
            // si-y-solo-si (rechazo): alguna precondicion falla => se lanza excepcion
            // y NO se persiste ninguna Orden_Fabricacion.
            assertThatThrownBy(() -> montaje.servicio().generar(cotizacionId))
                    .as("con alguna precondicion incumplida la generacion se rechaza")
                    .isInstanceOf(RuntimeException.class);
            verify(montaje.repositorio(), never()).saveAndFlush(any(OrdenFabricacion.class));
        }
    }

    // Feature: crm-anuncios-luminosos, Property 7: Para cualquier Cotizacion, la generación de una Orden_Fabricacion se permite si y solo si la Cotizacion está en estado "aprobada", no tiene ya una Orden_Fabricacion vinculada, y existe al menos una Prueba_Diseno en estado "aprobada".
    @Property(tries = 1000)
    void alRechazarSeLanzaLaExcepcionDeLaPrimeraPrecondicionIncumplida(
            @ForAll boolean cotizacionExiste,
            @ForAll("estadosCotizacion") String estadoCotizacion,
            @ForAll boolean yaTieneOF,
            @ForAll boolean tienePruebaAprobada) {

        UUID cotizacionId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        Montaje montaje = montar(cotizacionId, clienteId, cotizacionExiste, estadoCotizacion,
                yaTieneOF, tienePruebaAprobada);

        boolean deberiaTenerExito = cotizacionExiste
                && estaAprobada(estadoCotizacion)
                && !yaTieneOF
                && tienePruebaAprobada;

        // Solo interesan los casos de rechazo: se comprueba el TIPO de excepcion de
        // la PRIMERA precondicion incumplida, respetando el orden 0->1->2->3.
        if (deberiaTenerExito) {
            return;
        }

        if (!cotizacionExiste) {
            // Precondicion 0: 404, sin importar el resto.
            assertThatThrownBy(() -> montaje.servicio().generar(cotizacionId))
                    .as("Cotizacion inexistente => 404 (RecursoNoEncontradoException)")
                    .isInstanceOf(RecursoNoEncontradoException.class);
        } else if (!estaAprobada(estadoCotizacion)) {
            // Precondicion 1: 422 con el mensaje de Cotizacion aprobada.
            assertThatThrownBy(() -> montaje.servicio().generar(cotizacionId))
                    .as("Cotizacion no aprobada => 422 (ReglaNegocioException)")
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("se requiere una Cotizacion aprobada");
        } else if (yaTieneOF) {
            // Precondicion 2: 409.
            assertThatThrownBy(() -> montaje.servicio().generar(cotizacionId))
                    .as("Cotizacion con OF previa => 409 (ConflictoUnicidadException)")
                    .isInstanceOf(ConflictoUnicidadException.class);
        } else {
            // Precondicion 3 (unica restante): 422 con el mensaje de Prueba_Diseno.
            assertThat(tienePruebaAprobada)
                    .as("caso restante: solo falla la Prueba_Diseno aprobada")
                    .isFalse();
            assertThatThrownBy(() -> montaje.servicio().generar(cotizacionId))
                    .as("sin Prueba_Diseno aprobada => 422 (ReglaNegocioException)")
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("se requiere una Prueba_Diseno aprobada");
        }

        // En todo rechazo, jamas se persiste una Orden_Fabricacion.
        verify(montaje.repositorio(), never()).saveAndFlush(any(OrdenFabricacion.class));
    }

    // Feature: crm-anuncios-luminosos, Property 7: Para cualquier Cotizacion, la generación de una Orden_Fabricacion se permite si y solo si la Cotizacion está en estado "aprobada", no tiene ya una Orden_Fabricacion vinculada, y existe al menos una Prueba_Diseno en estado "aprobada".
    @Property(tries = 1000)
    void laPrecedenciaAntecedeALasPrecondicionesPosteriores(
            @ForAll("estadosNoAprobados") String estadoNoAprobado,
            @ForAll boolean yaTieneOF,
            @ForAll boolean tienePruebaAprobada) {

        // La Cotizacion existe pero NO esta aprobada; aunque coexistan otros
        // incumplimientos (OF previa, sin Prueba_Diseno), debe primar la
        // precondicion 1 (estado) por el orden 0->1->2->3 => 422 de Cotizacion.
        UUID cotizacionId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        Montaje montaje = montar(cotizacionId, clienteId, true, estadoNoAprobado,
                yaTieneOF, tienePruebaAprobada);

        assertThatThrownBy(() -> montaje.servicio().generar(cotizacionId))
                .as("estado no aprobado antecede a unicidad y a Prueba_Diseno")
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("se requiere una Cotizacion aprobada");
        verify(montaje.repositorio(), never()).saveAndFlush(any(OrdenFabricacion.class));
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Estado de Cotizacion (etiqueta ASCII); solo {@code aprobada} habilita. */
    @Provide
    Arbitrary<String> estadosCotizacion() {
        return Arbitraries.of("aprobada", "borrador", "enviada", "rechazada");
    }

    /** Estados de Cotizacion que NO son {@code aprobada}. */
    @Provide
    Arbitrary<String> estadosNoAprobados() {
        return Arbitraries.of("borrador", "enviada", "rechazada");
    }

    /**
     * Par (servicio bajo prueba, repositorio mockeado) para poder verificar la
     * ausencia/presencia de {@code saveAndFlush} sobre el mismo mock inyectado.
     */
    private record Montaje(ServicioOrdenesFabricacion servicio, OrdenFabricacionRepository repositorio) {
    }
}
