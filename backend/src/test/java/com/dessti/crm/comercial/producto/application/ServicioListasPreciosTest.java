package com.dessti.crm.comercial.producto.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.comercial.producto.adapter.out.persistence.ListaPreciosRepository;
import com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository;
import com.dessti.crm.comercial.producto.adapter.out.persistence.ProductoRepository;
import com.dessti.crm.comercial.producto.domain.ListaPrecios;
import com.dessti.crm.comercial.producto.domain.PrecioProducto;
import com.dessti.crm.comercial.producto.domain.Producto;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioListasPrecios} (Req 59.3, 59.9, 59.10).
 * Usan dobles de Mockito. Cubren la definicion de lista (59.3), la asignacion de
 * precio dentro de rango, el rechazo de precio fuera de rango (59.10 -> 422), el
 * Producto inexistente -> 404 y la auditoria (59.8).
 */
class ServicioListasPreciosTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ListaPreciosRepository listaPreciosRepository;
    private PrecioProductoRepository precioProductoRepository;
    private ProductoRepository productoRepository;
    private AuditoriaPort auditoria;
    private ServicioListasPrecios servicio;

    @BeforeEach
    void setUp() {
        listaPreciosRepository = mock(ListaPreciosRepository.class);
        precioProductoRepository = mock(PrecioProductoRepository.class);
        productoRepository = mock(ProductoRepository.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioListasPrecios(
                listaPreciosRepository, precioProductoRepository, productoRepository, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("definirLista persiste la lista con vigencia y audita (Req 59.3, 59.8)")
    void definirListaExitoso() {
        when(listaPreciosRepository.save(any(ListaPrecios.class))).thenAnswer(inv -> inv.getArgument(0));

        ListaPreciosDto dto = servicio.definirLista(new DefinirListaPreciosCommand(
                "Lista general 2024", 5, null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));

        assertThat(dto.nombre()).isEqualTo("Lista general 2024");
        assertThat(dto.prioridad()).isEqualTo(5);
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().recurso()).isEqualTo("lista_precios");
        assertThat(ev.getValue().accion()).isEqualTo("crear");
    }

    @Test
    @DisplayName("definirLista con vigencia_fin anterior al inicio se rechaza con 422 (Req 59.3)")
    void definirListaVigenciaInvalida() {
        assertThatThrownBy(() -> servicio.definirLista(new DefinirListaPreciosCommand(
                "Lista", 1, null, LocalDate.of(2024, 12, 31), LocalDate.of(2024, 1, 1))))
                .isInstanceOf(ReglaNegocioException.class);

        verify(listaPreciosRepository, never()).save(any());
    }

    @Test
    @DisplayName("asignarPrecio dentro de rango persiste el precio y audita (Req 59.3)")
    void asignarPrecioValido() {
        ListaPrecios lista = ListaPrecios.crear("Lista", 1, null, LocalDate.of(2024, 1, 1), null, "ventas");
        Producto producto = Producto.crear("Pantalla", "pieza", "desc", null, null, null, null, "ventas");
        when(listaPreciosRepository.findByIdAndActivoTrue(lista.getId())).thenReturn(Optional.of(lista));
        when(productoRepository.findByIdAndActivoTrue(producto.getId())).thenReturn(Optional.of(producto));
        when(precioProductoRepository.findByListaPreciosIdAndProductoId(lista.getId(), producto.getId()))
                .thenReturn(Optional.empty());
        when(precioProductoRepository.saveAndFlush(any(PrecioProducto.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PrecioProductoDto dto = servicio.asignarPrecio(lista.getId(),
                new AsignarPrecioCommand(producto.getId(), new BigDecimal("1500.50")));

        assertThat(dto.precio()).isEqualByComparingTo("1500.50");
        assertThat(dto.productoId()).isEqualTo(producto.getId());
        verify(auditoria).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("asignarPrecio fuera de rango se rechaza con 422 y no persiste (Req 59.10)")
    void asignarPrecioFueraDeRango() {
        ListaPrecios lista = ListaPrecios.crear("Lista", 1, null, LocalDate.of(2024, 1, 1), null, "ventas");
        Producto producto = Producto.crear("Pantalla", "pieza", "desc", null, null, null, null, "ventas");
        when(listaPreciosRepository.findByIdAndActivoTrue(lista.getId())).thenReturn(Optional.of(lista));
        when(productoRepository.findByIdAndActivoTrue(producto.getId())).thenReturn(Optional.of(producto));
        when(precioProductoRepository.findByListaPreciosIdAndProductoId(lista.getId(), producto.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarPrecio(lista.getId(),
                new AsignarPrecioCommand(producto.getId(), new BigDecimal("0.00"))))
                .isInstanceOf(ReglaNegocioException.class);

        verify(precioProductoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("asignarPrecio a un Producto inexistente/otro tenant devuelve 404 (Req 23.3)")
    void asignarPrecioProductoInexistente() {
        ListaPrecios lista = ListaPrecios.crear("Lista", 1, null, LocalDate.of(2024, 1, 1), null, "ventas");
        UUID productoAjeno = UUID.randomUUID();
        when(listaPreciosRepository.findByIdAndActivoTrue(lista.getId())).thenReturn(Optional.of(lista));
        when(productoRepository.findByIdAndActivoTrue(productoAjeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarPrecio(lista.getId(),
                new AsignarPrecioCommand(productoAjeno, new BigDecimal("10.00"))))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(precioProductoRepository, never()).saveAndFlush(any());
    }
}
