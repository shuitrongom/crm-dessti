package com.dessti.crm.operacion.proyecto.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterialPort;
import com.dessti.crm.operacion.produccion.application.CotizacionParaFabricacionPort;
import com.dessti.crm.operacion.produccion.application.CrearOrdenDirectaCommand;
import com.dessti.crm.operacion.produccion.application.MaterialAccesiblePort;
import com.dessti.crm.operacion.produccion.application.OrdenFabricacionDto;
import com.dessti.crm.operacion.produccion.application.ServicioOrdenesFabricacion;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.PartidaOrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.ProyectoRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.SitioRepository;
import com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro;
import com.dessti.crm.operacion.proyecto.domain.Proyecto;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
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
import org.mockito.ArgumentCaptor;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 6: verificacion de
 * existencia del Cliente</strong> (Req 4.1, 4.2, 4.3, 1.4, 1.5), aplicada a los
 * <strong>dos</strong> puntos de creacion que comparten el mismo puerto
 * {@link ClienteExistentePort}:
 * <ul>
 *   <li>{@link ServicioProyectos#crear(CrearProyectoCommand)} — Proyecto (§A4).</li>
 *   <li>{@link ServicioOrdenesFabricacion#crearDirecta(CrearOrdenDirectaCommand)} —
 *       Orden_Fabricacion directa (§A1, Req 1.5).</li>
 * </ul>
 *
 * <p>Enunciado (para todo {@code clienteId} y toda decision de existencia):</p>
 * <ul>
 *   <li>{@code clienteId} nulo &rarr; <strong>422</strong>
 *       ({@link ReglaNegocioException}) sin persistir el recurso
 *       (nunca se invoca {@code repo.save}).</li>
 *   <li>{@code clienteId} no nulo pero {@code existeEnTenant -> false} &rarr;
 *       <strong>404</strong> ({@link RecursoNoEncontradoException}) sin persistir,
 *       y se audita el acceso cruzado con recurso {@code cliente}.</li>
 *   <li>{@code clienteId} no nulo y {@code existeEnTenant -> true} &rarr; crea el
 *       recurso (Proyecto en {@code SIN_SITIOS} / OF directa en {@code pendiente}).</li>
 * </ul>
 *
 * <p>Se reutilizan las piezas de produccion tal cual, inyectando como <em>mocks</em>
 * de Mockito el resto de colaboradores para aislar la unica dimension bajo prueba:
 * la existencia del Cliente. No hay contexto de Spring, ni base de datos, ni
 * Testcontainers.</p>
 *
 * <p><strong>Aislamiento entre intentos:</strong> los servicios auditan via
 * {@link TenantContext#require()} (un thread-local) y jqwik reutiliza el hilo entre
 * intentos; {@link #limpiarContexto()} (anotado {@link AfterTry}) limpia el
 * {@link TenantContext} tras cada intento para evitar fugas de estado.</p>
 */
@Label("Feature: operacion-produccion-enterprise, Property 6: verificacion de existencia del Cliente "
        + "(nulo->422; inexistente->404 sin persistir + auditoria; existente->crea el recurso)")
class VerificacionClientePropertyTest {

    // ----------------------------------------------------------------------
    // Aislamiento entre intentos: TenantContext es thread-local y jqwik
    // reutiliza el hilo. Se limpia el tenant tras cada try.
    // ----------------------------------------------------------------------
    @AfterTry
    void limpiarContexto() {
        TenantContext.clear();
    }

    // ======================================================================
    // Property 6 sobre ServicioProyectos.crear
    // ======================================================================

    @Property(tries = 200)
    void proyectoCrearVerificaExistenciaDelCliente(
            @ForAll boolean existeCliente,
            @ForAll("clienteIdOpcional") UUID clienteId) {

        // El tenant debe estar en contexto: el servicio audita via TenantContext.require().
        TenantContext.set(UUID.randomUUID());
        boolean clienteNulo = (clienteId == null);

        ProyectoRepository proyectoRepository = mock(ProyectoRepository.class);
        SitioRepository sitioRepository = mock(SitioRepository.class);
        ClienteExistentePort clienteExistente = mock(ClienteExistentePort.class);
        PerfilFasesGiroPort perfilFasesGiro = mock(PerfilFasesGiroPort.class);
        AvanceSitioPort avanceProduccion = mock(AvanceSitioPort.class);
        AvanceSitioPort avanceSitioAnuncios = mock(AvanceSitioPort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        // Colaboradores no ejercitados en crear(): stubs neutrales/lenient.
        lenient().when(perfilFasesGiro.perfilDelTenant()).thenReturn(PerfilFasesGiro.GENERICO);
        // save(...) devuelve la misma entidad recibida (materializa la creacion).
        lenient().when(proyectoRepository.save(any(Proyecto.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // clienteId lo genera jqwik (a veces null, a veces un UUID distinto).
        boolean existe = existeCliente;
        lenient().when(clienteExistente.existeEnTenant(any(UUID.class))).thenReturn(existe);

        ServicioProyectos servicio = new ServicioProyectos(
                proyectoRepository, sitioRepository, clienteExistente, perfilFasesGiro,
                avanceProduccion, avanceSitioAnuncios, auditoria);

        CrearProyectoCommand comando = new CrearProyectoCommand(clienteId, "Proyecto de prueba");

        if (clienteNulo) {
            // clienteId nulo -> 422 sin persistir (Req 4.3): la verificacion de
            // existencia ni siquiera se consulta.
            assertThatThrownBy(() -> servicio.crear(comando))
                    .as("clienteId nulo debe rechazarse con 422")
                    .isInstanceOf(ReglaNegocioException.class);
            verify(proyectoRepository, never()).save(any(Proyecto.class));
        } else if (!existe) {
            // Cliente inexistente/no accesible -> 404 sin persistir + auditoria de
            // acceso cruzado con recurso 'cliente' (Req 4.2).
            assertThatThrownBy(() -> servicio.crear(comando))
                    .as("Cliente inexistente debe rechazarse con 404")
                    .isInstanceOf(RecursoNoEncontradoException.class);
            verify(proyectoRepository, never()).save(any(Proyecto.class));
            verificarAuditoriaAccesoCruzadoCliente(auditoria);
        } else {
            // Cliente existente -> crea el Proyecto en SIN_SITIOS (Req 4.4).
            ProyectoDto dto = servicio.crear(comando);
            assertThat(dto).as("la creacion exitosa devuelve un DTO").isNotNull();
            assertThat(dto.estadoConsolidado())
                    .as("un Proyecto recien creado queda en 'sin_sitios'")
                    .isEqualTo("sin_sitios");
            verify(proyectoRepository).save(any(Proyecto.class));
        }
    }

    // ======================================================================
    // Property 6 sobre ServicioOrdenesFabricacion.crearDirecta
    // ======================================================================

    @Property(tries = 200)
    void ordenFabricacionCrearDirectaVerificaExistenciaDelCliente(
            @ForAll boolean existeCliente,
            @ForAll("clienteIdOpcional") UUID clienteId) {

        TenantContext.set(UUID.randomUUID());
        boolean clienteNulo = (clienteId == null);

        OrdenFabricacionRepository ordenRepository = mock(OrdenFabricacionRepository.class);
        PartidaOrdenFabricacionRepository partidaRepository = mock(PartidaOrdenFabricacionRepository.class);
        CotizacionParaFabricacionPort cotizacionParaFabricacion = mock(CotizacionParaFabricacionPort.class);
        PruebaDisenoAprobadaPort pruebaDisenoAprobada = mock(PruebaDisenoAprobadaPort.class);
        ClienteExistentePort clienteExistente = mock(ClienteExistentePort.class);
        MaterialAccesiblePort materialAccesible = mock(MaterialAccesiblePort.class);
        ConsumoMaterialPort consumoMaterialPort = mock(ConsumoMaterialPort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        // save(...) devuelve la misma entidad recibida (materializa la creacion).
        lenient().when(ordenRepository.save(any(OrdenFabricacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Camino feliz sin partidas: no se requiere materialAccesible; aun asi se deja
        // como accesible por robustez ante cualquier ruta.
        lenient().when(materialAccesible.esAccesible(any(UUID.class))).thenReturn(true);
        // Colaboradores del camino desde Cotizacion, no ejercitados en crearDirecta.
        lenient().when(cotizacionParaFabricacion.buscarParaFabricacion(any(UUID.class)))
                .thenReturn(Optional.empty());

        boolean existe = existeCliente;
        lenient().when(clienteExistente.existeEnTenant(any(UUID.class))).thenReturn(existe);

        ServicioOrdenesFabricacion servicio = new ServicioOrdenesFabricacion(
                ordenRepository, partidaRepository, cotizacionParaFabricacion,
                pruebaDisenoAprobada, clienteExistente, materialAccesible,
                consumoMaterialPort, auditoria);

        // Command con lista de partidas vacia: aisla la verificacion del Cliente del
        // camino de partidas/Material (camino feliz sin consumo).
        CrearOrdenDirectaCommand comando = new CrearOrdenDirectaCommand(clienteId, List.of());

        if (clienteNulo) {
            // clienteId nulo -> 422 sin persistir (Req 1.4).
            assertThatThrownBy(() -> servicio.crearDirecta(comando))
                    .as("clienteId nulo debe rechazarse con 422")
                    .isInstanceOf(ReglaNegocioException.class);
            verify(ordenRepository, never()).save(any(OrdenFabricacion.class));
        } else if (!existe) {
            // Cliente inexistente -> 404 sin persistir + auditoria de acceso cruzado
            // con recurso 'cliente' (Req 1.5).
            assertThatThrownBy(() -> servicio.crearDirecta(comando))
                    .as("Cliente inexistente debe rechazarse con 404")
                    .isInstanceOf(RecursoNoEncontradoException.class);
            verify(ordenRepository, never()).save(any(OrdenFabricacion.class));
            verificarAuditoriaAccesoCruzadoCliente(auditoria);
        } else {
            // Cliente existente -> crea la OF directa en 'pendiente' (Req 1.2).
            OrdenFabricacionDto dto = servicio.crearDirecta(comando);
            assertThat(dto).as("la creacion exitosa devuelve un DTO").isNotNull();
            assertThat(dto.estado())
                    .as("una OF directa recien creada queda en 'pendiente'")
                    .isEqualTo("pendiente");
            assertThat(dto.cotizacionId())
                    .as("la OF directa no tiene Cotizacion de origen")
                    .isNull();
            verify(ordenRepository).save(any(OrdenFabricacion.class));
        }
    }

    // ----------------------------------------------------------------------
    // Utilidad de verificacion de la auditoria del acceso cruzado al Cliente.
    // ----------------------------------------------------------------------

    /**
     * Verifica que se registro al menos un evento de auditoria de acceso cruzado con
     * recurso {@code cliente} (Req 4.2, 1.5). Ambos servicios auditan el intento con
     * la accion {@code acceso_denegado} sobre el recurso {@code cliente}.
     */
    private static void verificarAuditoriaAccesoCruzadoCliente(AuditoriaPort auditoria) {
        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.recurso())
                .as("el acceso cruzado se audita con recurso 'cliente'")
                .isEqualTo("cliente");
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Genera aleatoriamente un {@code clienteId}: la mayoria de las veces un UUID
     * distinto y, con probabilidad {@code 1/5}, {@code null}. Al no ser un dominio
     * finito, jqwik genera de forma aleatoria (no exhaustiva) y honra el numero de
     * intentos de la property (&ge; 100).
     */
    @Provide
    Arbitrary<UUID> clienteIdOpcional() {
        return Arbitraries.oneOf(
                Arbitraries.randomValue(random -> UUID.randomUUID()),
                Arbitraries.randomValue(random -> UUID.randomUUID()),
                Arbitraries.randomValue(random -> UUID.randomUUID()),
                Arbitraries.randomValue(random -> UUID.randomUUID()),
                Arbitraries.just(null));
    }
}
