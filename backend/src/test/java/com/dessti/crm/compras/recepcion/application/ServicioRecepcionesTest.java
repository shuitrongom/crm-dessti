package com.dessti.crm.compras.recepcion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

import com.dessti.crm.compras.ordencompra.adapter.out.persistence.OrdenCompraRepository;
import com.dessti.crm.compras.ordencompra.domain.EstadoOrdenCompra;
import com.dessti.crm.compras.ordencompra.domain.OrdenCompra;
import com.dessti.crm.compras.ordencompra.domain.PartidaOrdenCompra;
import com.dessti.crm.compras.recepcion.adapter.out.persistence.RecepcionMercanciaRepository;
import com.dessti.crm.compras.recepcion.domain.RecepcionMercancia;
import com.dessti.crm.operacion.inventario.application.RecepcionMaterialPort;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) del {@link ServicioRecepciones} (Req 32): registro
 * de una recepción con actualización de inventario y derivación del estado de la
 * Orden_Compra; rechazo cuando la orden no admite recepciones (422), cuando el
 * acumulado excede lo ordenado (422, Property 10) y 404 si la orden no es
 * accesible. No arranca Spring ni base de datos: dobla el repositorio, el puerto de
 * inventario y la auditoría; el tenant se fija.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServicioRecepciones (Req 32)")
class ServicioRecepcionesTest {

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PROVEEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MATERIAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private RecepcionMercanciaRepository recepcionRepository;
    @Mock private OrdenCompraRepository ordenCompraRepository;
    @Mock private RecepcionMaterialPort recepcionMaterial;
    @Mock private AuditoriaPort auditoria;

    private ServicioRecepciones servicio;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2024-05-01T12:00:00Z"), ZoneOffset.UTC);
        servicio = new ServicioRecepciones(
                recepcionRepository, ordenCompraRepository, recepcionMaterial, auditoria, clock);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Orden abierta con una sola partida de 10 unidades. */
    private static OrdenCompra ordenAbierta() {
        return OrdenCompra.crear(PROVEEDOR_ID, null,
                List.of(PartidaOrdenCompra.crear(MATERIAL_ID, 10, new BigDecimal("100.00"), "sistema")),
                "sistema");
    }

    private static RegistrarRecepcionCommand comando(UUID partidaId, BigDecimal cantidad) {
        return new RegistrarRecepcionCommand(UUID.randomUUID(),
                List.of(new RegistrarPartidaRecepcionCommand(partidaId, cantidad)));
    }

    @Test
    @DisplayName("registrar recepción parcial: guarda, ingresa inventario y deja la orden en recibida_parcial")
    void registrarParcial() {
        OrdenCompra orden = ordenAbierta();
        UUID partidaId = orden.getPartidas().get(0).getId();
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.of(orden));
        when(recepcionRepository.sumarRecibidoPorPartida(any())).thenReturn(List.of());
        when(recepcionRepository.save(any(RecepcionMercancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(recepcionMaterial.recibirDeOrdenCompra(any(), anyList())).thenReturn(List.of());

        // Recibe 4 de 10 → recepción parcial.
        RecepcionMercanciaDto dto = servicio.registrarRecepcion(comando(partidaId, new BigDecimal("4")));

        assertThat(dto).isNotNull();
        assertThat(dto.partidas()).hasSize(1);
        verify(recepcionRepository).save(any(RecepcionMercancia.class));
        verify(recepcionMaterial).recibirDeOrdenCompra(any(), anyList());
        // Se persiste la orden con su nuevo estado derivado (abierta -> recibida_parcial).
        verify(ordenCompraRepository, times(1)).save(orden);
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenCompra.RECIBIDA_PARCIAL);
    }

    @Test
    @DisplayName("registrar recepción total: deja la orden en recibida_total")
    void registrarTotal() {
        OrdenCompra orden = ordenAbierta();
        UUID partidaId = orden.getPartidas().get(0).getId();
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.of(orden));
        when(recepcionRepository.sumarRecibidoPorPartida(any())).thenReturn(List.of());
        when(recepcionRepository.save(any(RecepcionMercancia.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(recepcionMaterial.recibirDeOrdenCompra(any(), anyList())).thenReturn(List.of());

        // Recibe las 10 de 10 → recepción total.
        servicio.registrarRecepcion(comando(partidaId, new BigDecimal("10")));

        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenCompra.RECIBIDA_TOTAL);
    }

    @Test
    @DisplayName("rechaza (422) cuando el acumulado recibido excede lo ordenado, sin tocar inventario")
    void excedeOrdenado() {
        OrdenCompra orden = ordenAbierta();
        UUID partidaId = orden.getPartidas().get(0).getId();
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.of(orden));
        when(recepcionRepository.sumarRecibidoPorPartida(any())).thenReturn(List.of());

        // Recibe 11 de 10 → excede.
        assertThatThrownBy(() -> servicio.registrarRecepcion(comando(partidaId, new BigDecimal("11"))))
                .isInstanceOf(ReglaNegocioException.class);

        verify(recepcionRepository, never()).save(any());
        verify(recepcionMaterial, never()).recibirDeOrdenCompra(any(), anyList());
    }

    @Test
    @DisplayName("rechaza (422) cuando la orden no admite recepciones por su estado")
    void ordenNoAdmiteRecepcion() {
        OrdenCompra orden = ordenAbierta();
        orden.cambiarEstado(EstadoOrdenCompra.CANCELADA, "sistema");
        UUID partidaId = orden.getPartidas().get(0).getId();
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> servicio.registrarRecepcion(comando(partidaId, new BigDecimal("1"))))
                .isInstanceOf(ReglaNegocioException.class);

        verify(recepcionRepository, never()).save(any());
        verify(recepcionMaterial, never()).recibirDeOrdenCompra(any(), anyList());
    }

    @Test
    @DisplayName("rechaza (404) cuando la Orden_Compra no es accesible en el tenant")
    void ordenInexistente() {
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrarRecepcion(
                comando(UUID.randomUUID(), new BigDecimal("1"))))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(recepcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("rechaza (422) cuando el renglón no corresponde a una partida de la orden")
    void renglonNoCorrespondeALaOrden() {
        OrdenCompra orden = ordenAbierta();
        when(ordenCompraRepository.findById(any())).thenReturn(Optional.of(orden));
        when(recepcionRepository.sumarRecibidoPorPartida(any())).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.registrarRecepcion(
                comando(UUID.randomUUID(), new BigDecimal("1"))))
                .isInstanceOf(ReglaNegocioException.class);

        verify(recepcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("consultar propaga 404 si la recepción no es accesible")
    void consultarInexistente() {
        UUID recepcionId = UUID.randomUUID();
        when(recepcionRepository.findById(eq(recepcionId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(recepcionId))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
