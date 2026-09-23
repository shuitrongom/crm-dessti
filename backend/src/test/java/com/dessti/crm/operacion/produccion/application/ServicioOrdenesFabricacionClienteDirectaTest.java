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
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterialPort;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.PartidaOrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.operacion.produccion.application.PruebaDisenoAprobadaPort;

/**
 * Pruebas unitarias de <strong>ejemplo</strong> de la verificacion de existencia del
 * Cliente en {@link ServicioOrdenesFabricacion#crearDirecta(CrearOrdenDirectaCommand)}
 * (Req 4.2, 4.3, 4.4, 1.4, 1.5, 14.5). Complementan la {@code Property 6} (cubierta
 * por {@code VerificacionClientePropertyTest}) con ejemplos y bordes concretos, en el
 * mismo estilo que {@code ServicioOrdenesFabricacionTest}: JUnit + Mockito, sin
 * arrancar Spring ni base de datos.
 *
 * <p>Casos cubiertos (con lista de partidas vacia, para aislar la verificacion del
 * Cliente del camino de partidas/Material):</p>
 * <ul>
 *   <li>Cliente inexistente/no accesible &rarr; 404 + auditoria de acceso cruzado con
 *       recurso {@code cliente}, sin persistir la OF (Req 1.5).</li>
 *   <li>{@code clienteId} nulo &rarr; 422 sin persistir (no se invoca {@code save}) ni
 *       se consulta la existencia del Cliente (Req 1.4).</li>
 *   <li>Cliente existente &rarr; crea la OF directa en {@code pendiente}
 *       ({@code cotizacionId = null}) y audita la creacion (Req 1.2).</li>
 * </ul>
 */
class ServicioOrdenesFabricacionClienteDirectaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private OrdenFabricacionRepository repositorio;
    private PartidaOrdenFabricacionRepository partidaRepositorio;
    private CotizacionParaFabricacionPort cotizacionParaFabricacion;
    private PruebaDisenoAprobadaPort pruebaDisenoAprobada;
    private ClienteExistentePort clienteExistente;
    private MaterialAccesiblePort materialAccesible;
    private ConsumoMaterialPort consumoMaterialPort;
    private AuditoriaPort auditoria;
    private ServicioOrdenesFabricacion servicio;

    @BeforeEach
    void setUp() {
        repositorio = mock(OrdenFabricacionRepository.class);
        partidaRepositorio = mock(PartidaOrdenFabricacionRepository.class);
        cotizacionParaFabricacion = mock(CotizacionParaFabricacionPort.class);
        pruebaDisenoAprobada = mock(PruebaDisenoAprobadaPort.class);
        clienteExistente = mock(ClienteExistentePort.class);
        materialAccesible = mock(MaterialAccesiblePort.class);
        consumoMaterialPort = mock(ConsumoMaterialPort.class);
        auditoria = mock(AuditoriaPort.class);
        // save(...) devuelve la misma entidad recibida (materializa la creacion).
        lenient().when(repositorio.save(any(OrdenFabricacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        servicio = new ServicioOrdenesFabricacion(
                repositorio, partidaRepositorio, cotizacionParaFabricacion, pruebaDisenoAprobada,
                clienteExistente, materialAccesible, consumoMaterialPort, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("crearDirecta con Cliente inexistente devuelve 404 y audita el acceso cruzado con recurso 'cliente' (Req 1.5)")
    void crearDirectaClienteInexistente() {
        when(clienteExistente.existeEnTenant(CLIENTE)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crearDirecta(new CrearOrdenDirectaCommand(CLIENTE, List.of())))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any(OrdenFabricacion.class));
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cliente");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("crearDirecta con clienteId nulo devuelve 422 sin persistir ni verificar existencia (Req 1.4)")
    void crearDirectaClienteNulo() {
        assertThatThrownBy(() -> servicio.crearDirecta(new CrearOrdenDirectaCommand(null, List.of())))
                .isInstanceOf(ReglaNegocioException.class);

        verify(repositorio, never()).save(any(OrdenFabricacion.class));
        verify(clienteExistente, never()).existeEnTenant(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("crearDirecta con Cliente existente crea la OF directa en 'pendiente' sin Cotizacion y audita (Req 1.2)")
    void crearDirectaClienteExistente() {
        when(clienteExistente.existeEnTenant(CLIENTE)).thenReturn(true);

        OrdenFabricacionDto dto = servicio.crearDirecta(new CrearOrdenDirectaCommand(CLIENTE, List.of()));

        assertThat(dto).isNotNull();
        assertThat(dto.estado()).isEqualTo("pendiente");
        assertThat(dto.cotizacionId()).isNull();
        assertThat(dto.clienteId()).isEqualTo(CLIENTE);
        verify(repositorio).save(any(OrdenFabricacion.class));

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear_directa");
        assertThat(ev.getValue().recurso()).isEqualTo("orden_fabricacion");
        assertThat(ev.getValue().valorNuevo()).isEqualTo("pendiente");
    }
}
