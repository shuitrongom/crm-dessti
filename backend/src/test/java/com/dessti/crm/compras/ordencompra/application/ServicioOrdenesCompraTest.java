package com.dessti.crm.compras.ordencompra.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.dessti.crm.compras.ordencompra.adapter.out.persistence.OrdenCompraRepository;
import com.dessti.crm.compras.ordencompra.domain.EstadoOrdenCompra;
import com.dessti.crm.compras.ordencompra.domain.OrdenCompra;
import com.dessti.crm.compras.ordencompra.domain.PartidaOrdenCompra;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) del {@link ServicioOrdenesCompra} (Req 31): alta con
 * validación de proveedor y materiales, cálculo del total, generación desde requisición,
 * y máquina de estados (cancelar/cerrar; transición inválida 409). No arranca Spring ni
 * base de datos: dobla el repositorio, los puertos y la auditoría; el tenant se fija.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServicioOrdenesCompra (Req 31)")
class ServicioOrdenesCompraTest {

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PROVEEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MATERIAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REQUISICION_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Mock private OrdenCompraRepository ordenCompraRepository;
    @Mock private ProveedorExistentePort proveedorExistente;
    @Mock private MaterialExistentePort materialExistente;
    @Mock private AuditoriaPort auditoria;

    private ServicioOrdenesCompra servicio;

    @BeforeEach
    void setUp() {
        servicio = new ServicioOrdenesCompra(
                ordenCompraRepository, proveedorExistente, materialExistente, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static CrearOrdenCompraCommand comando() {
        return new CrearOrdenCompraCommand(PROVEEDOR_ID, null,
                List.of(new CrearPartidaOrdenCompraCommand(MATERIAL_ID, 3, new BigDecimal("100.00"))));
    }

    @Test
    @DisplayName("crear valida proveedor y material, calcula el total y persiste en abierta")
    void crearPersiste() {
        when(proveedorExistente.existeProveedorActivo(PROVEEDOR_ID)).thenReturn(true);
        when(materialExistente.existeMaterialActivo(MATERIAL_ID)).thenReturn(true);
        when(ordenCompraRepository.save(any(OrdenCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenCompraDto dto = servicio.crearOrdenCompra(comando());

        assertThat(dto.estado()).isEqualTo("abierta");
        assertThat(dto.total()).isEqualByComparingTo("300.00"); // 3 × 100.00
        verify(ordenCompraRepository).save(any(OrdenCompra.class));
    }

    @Test
    @DisplayName("crear rechaza (404) si el proveedor no existe o no está activo")
    void crearProveedorInexistente() {
        when(proveedorExistente.existeProveedorActivo(PROVEEDOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crearOrdenCompra(comando()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(ordenCompraRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear rechaza (404) si algún material no existe o no está activo")
    void crearMaterialInexistente() {
        when(proveedorExistente.existeProveedorActivo(PROVEEDOR_ID)).thenReturn(true);
        when(materialExistente.existeMaterialActivo(MATERIAL_ID)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crearOrdenCompra(comando()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(ordenCompraRepository, never()).save(any());
    }

    @Test
    @DisplayName("crearDesdeRequisicion vincula la orden a la requisición de origen")
    void crearDesdeRequisicion() {
        when(proveedorExistente.existeProveedorActivo(PROVEEDOR_ID)).thenReturn(true);
        when(materialExistente.existeMaterialActivo(MATERIAL_ID)).thenReturn(true);
        when(ordenCompraRepository.save(any(OrdenCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID ordenId = servicio.crearDesdeRequisicion(REQUISICION_ID, comando(), "sistema");

        assertThat(ordenId).isNotNull();
        verify(ordenCompraRepository).save(any(OrdenCompra.class));
    }

    @Test
    @DisplayName("cancelar una orden abierta es una transición válida")
    void cancelarAbierta() {
        OrdenCompra orden = ordenAbierta();
        when(ordenCompraRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(ordenCompraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrdenCompraDto dto = servicio.cambiarEstado(orden.getId(), "cancelada");

        assertThat(dto.estado()).isEqualTo("cancelada");
    }

    @Test
    @DisplayName("cerrar una orden abierta es una transición inválida (409)")
    void cerrarAbiertaInvalida() {
        OrdenCompra orden = ordenAbierta();
        when(ordenCompraRepository.findById(orden.getId())).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> servicio.cambiarEstado(orden.getId(), "cerrada"))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("cambiar a un estado desconocido se rechaza con 422")
    void estadoDesconocido() {
        OrdenCompra orden = ordenAbierta();
        // interpretarEstado se ejecuta antes de cargar, así que no hace falta stub de findById.

        assertThatThrownBy(() -> servicio.cambiarEstado(orden.getId(), "inexistente"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    private static OrdenCompra ordenAbierta() {
        return OrdenCompra.crear(PROVEEDOR_ID, null,
                List.of(PartidaOrdenCompra.crear(MATERIAL_ID, 3, new BigDecimal("100.00"), "sistema")),
                "sistema");
    }
}
