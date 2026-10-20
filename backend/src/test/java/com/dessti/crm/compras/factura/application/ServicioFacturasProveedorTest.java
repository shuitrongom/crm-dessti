package com.dessti.crm.compras.factura.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dessti.crm.compras.factura.adapter.out.persistence.FacturaProveedorRepository;
import com.dessti.crm.compras.factura.domain.EstadoFacturaProveedor;
import com.dessti.crm.compras.factura.domain.FacturaProveedor;
import com.dessti.crm.compras.ordencompra.adapter.out.persistence.OrdenCompraRepository;
import com.dessti.crm.compras.ordencompra.domain.OrdenCompra;
import com.dessti.crm.compras.ordencompra.domain.PartidaOrdenCompra;
import com.dessti.crm.compras.recepcion.adapter.out.persistence.RecepcionMercanciaRepository;
import com.dessti.crm.compras.recepcion.adapter.out.persistence.RecepcionMercanciaRepository.RecibidoPorPartida;
import com.dessti.crm.contabilidad.cxp.application.CuentaPorPagarPort;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) del {@link ServicioFacturasProveedor} (Req 33):
 * registro derivando el proveedor de la orden; Conciliacion_Tres_Vias (conciliada
 * cuando lo recibido cubre lo ordenado; discrepancia cuando falta recepción);
 * autorización de pago solo desde conciliada (422 en otro caso); reapertura desde
 * discrepancia (422 en otro caso). No arranca Spring ni base de datos: dobla los
 * repositorios, el puerto de CxP y la auditoría; el tenant se fija.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServicioFacturasProveedor (Req 33)")
class ServicioFacturasProveedorTest {

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PROVEEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MATERIAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private FacturaProveedorRepository facturaRepository;
    @Mock private OrdenCompraRepository ordenCompraRepository;
    @Mock private RecepcionMercanciaRepository recepcionRepository;
    @Mock private AuditoriaPort auditoria;
    @Mock private CuentaPorPagarPort cuentaPorPagar;

    private ServicioFacturasProveedor servicio;

    @BeforeEach
    void setUp() {
        servicio = new ServicioFacturasProveedor(
                facturaRepository, ordenCompraRepository, recepcionRepository,
                new ConciliacionProperties(new BigDecimal("0.02")), auditoria, cuentaPorPagar);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Orden con una partida de 10 × 100.00. */
    private static OrdenCompra orden() {
        return OrdenCompra.crear(PROVEEDOR_ID, null,
                List.of(PartidaOrdenCompra.crear(MATERIAL_ID, 10, new BigDecimal("100.00"), "sistema")),
                "sistema");
    }

    /** Proyección de recibido acumulado para una partida. */
    private static RecibidoPorPartida recibido(UUID partidaId, BigDecimal cantidad) {
        return new RecibidoPorPartida() {
            @Override
            public UUID getPartidaOrdenCompraId() {
                return partidaId;
            }

            @Override
            public BigDecimal getRecibida() {
                return cantidad;
            }
        };
    }

    private static FacturaProveedor factura(EstadoFacturaProveedor estado, UUID ordenId) {
        FacturaProveedor f = FacturaProveedor.registrar(
                ordenId, PROVEEDOR_ID, "F-1001", new BigDecimal("1000.00"), "sistema");
        // Lleva la factura al estado deseado siguiendo transiciones válidas.
        if (estado == EstadoFacturaProveedor.CONCILIADA) {
            f.cambiarEstado(EstadoFacturaProveedor.CONCILIADA, "sistema");
        } else if (estado == EstadoFacturaProveedor.DISCREPANCIA) {
            f.cambiarEstado(EstadoFacturaProveedor.DISCREPANCIA, "sistema");
        }
        return f;
    }

    @Test
    @DisplayName("registrar deriva el proveedor de la orden y persiste en 'registrada'")
    void registrarPersiste() {
        OrdenCompra orden = orden();
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.of(orden));
        when(facturaRepository.save(any(FacturaProveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        FacturaProveedorDto dto = servicio.registrarFactura(
                new RegistrarFacturaProveedorCommand(orden.getId(), "F-1001", new BigDecimal("1000.00")));

        assertThat(dto.estado()).isEqualTo("registrada");
        assertThat(dto.proveedorId()).isEqualTo(PROVEEDOR_ID);
        verify(facturaRepository).save(any(FacturaProveedor.class));
    }

    @Test
    @DisplayName("registrar rechaza (404) si la orden no es accesible")
    void registrarOrdenInexistente() {
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrarFactura(
                new RegistrarFacturaProveedorCommand(UUID.randomUUID(), "F-1", new BigDecimal("1.00"))))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(facturaRepository, never()).save(any());
    }

    @Test
    @DisplayName("conciliar deja 'conciliada' y registra la CxP cuando lo recibido cubre lo ordenado")
    void conciliarSatisfactoria() {
        OrdenCompra orden = orden();
        UUID partidaId = orden.getPartidas().get(0).getId();
        FacturaProveedor f = factura(EstadoFacturaProveedor.REGISTRADA, orden.getId());
        when(facturaRepository.findById(eq(f.getId()))).thenReturn(Optional.of(f));
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.of(orden));
        when(recepcionRepository.sumarRecibidoPorPartida(any()))
                .thenReturn(List.of(recibido(partidaId, new BigDecimal("10"))));
        when(facturaRepository.save(any(FacturaProveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        FacturaProveedorDto dto = servicio.conciliar(f.getId());

        assertThat(dto.estado()).isEqualTo("conciliada");
        verify(cuentaPorPagar).registrarPorFacturaProveedorConciliada(
                eq(f.getId()), eq(PROVEEDOR_ID), any(BigDecimal.class));
    }

    @Test
    @DisplayName("conciliar deja 'discrepancia' (sin CxP) cuando falta recepción")
    void conciliarConDiscrepancia() {
        OrdenCompra orden = orden();
        UUID partidaId = orden.getPartidas().get(0).getId();
        FacturaProveedor f = factura(EstadoFacturaProveedor.REGISTRADA, orden.getId());
        when(facturaRepository.findById(eq(f.getId()))).thenReturn(Optional.of(f));
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.of(orden));
        // Solo se recibieron 4 de 10 → discrepancia de cantidad.
        when(recepcionRepository.sumarRecibidoPorPartida(any()))
                .thenReturn(List.of(recibido(partidaId, new BigDecimal("4"))));
        when(facturaRepository.save(any(FacturaProveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        FacturaProveedorDto dto = servicio.conciliar(f.getId());

        assertThat(dto.estado()).isEqualTo("discrepancia");
        verify(cuentaPorPagar, never()).registrarPorFacturaProveedorConciliada(any(), any(), any());
    }

    @Test
    @DisplayName("conciliar rechaza (422) si la factura no está en 'registrada'")
    void conciliarNoRegistrada() {
        FacturaProveedor f = factura(EstadoFacturaProveedor.CONCILIADA, UUID.randomUUID());
        when(facturaRepository.findById(eq(f.getId()))).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> servicio.conciliar(f.getId()))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("autorizarPago lleva 'conciliada' -> 'pagada'")
    void autorizarPagoDesdeConciliada() {
        FacturaProveedor f = factura(EstadoFacturaProveedor.CONCILIADA, UUID.randomUUID());
        when(facturaRepository.findById(eq(f.getId()))).thenReturn(Optional.of(f));
        when(facturaRepository.save(any(FacturaProveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        FacturaProveedorDto dto = servicio.autorizarPago(f.getId());

        assertThat(dto.estado()).isEqualTo("pagada");
    }

    @Test
    @DisplayName("autorizarPago rechaza (422) si la factura no está conciliada")
    void autorizarPagoNoConciliada() {
        FacturaProveedor f = factura(EstadoFacturaProveedor.REGISTRADA, UUID.randomUUID());
        when(facturaRepository.findById(eq(f.getId()))).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> servicio.autorizarPago(f.getId()))
                .isInstanceOf(ReglaNegocioException.class);

        verify(facturaRepository, never()).save(any());
    }

    @Test
    @DisplayName("reabrir lleva 'discrepancia' -> 'registrada'")
    void reabrirDesdeDiscrepancia() {
        FacturaProveedor f = factura(EstadoFacturaProveedor.DISCREPANCIA, UUID.randomUUID());
        when(facturaRepository.findById(eq(f.getId()))).thenReturn(Optional.of(f));
        when(facturaRepository.save(any(FacturaProveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        FacturaProveedorDto dto = servicio.reabrir(f.getId());

        assertThat(dto.estado()).isEqualTo("registrada");
    }

    @Test
    @DisplayName("reabrir rechaza (422) si la factura no está en 'discrepancia'")
    void reabrirNoDiscrepancia() {
        FacturaProveedor f = factura(EstadoFacturaProveedor.REGISTRADA, UUID.randomUUID());
        when(facturaRepository.findById(eq(f.getId()))).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> servicio.reabrir(f.getId()))
                .isInstanceOf(ReglaNegocioException.class);

        verify(facturaRepository, never()).save(any());
    }

    @Test
    @DisplayName("consultar propaga 404 si la factura no es accesible")
    void consultarInexistente() {
        UUID facturaId = UUID.randomUUID();
        when(facturaRepository.findById(eq(facturaId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(facturaId))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
