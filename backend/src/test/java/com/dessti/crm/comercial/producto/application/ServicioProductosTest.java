package com.dessti.crm.comercial.producto.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dessti.crm.comercial.producto.adapter.out.persistence.ProductoRepository;
import com.dessti.crm.comercial.producto.domain.Producto;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioProductos} (Req 59). Usan dobles de
 * Mockito; no arrancan Spring ni base de datos. Cubren alta exitosa (59.1),
 * rechazo de datos invalidos (59.2 -> 422), borrado logico (59.6), listado con
 * filtro (59.7), auditoria (59.8) y acceso cruzado -> 404 + auditoria (23.3).
 */
class ServicioProductosTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ProductoRepository productoRepository;
    private AuditoriaPort auditoria;
    private ServicioProductos servicio;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioProductos(productoRepository, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CrearProductoCommand comandoValido() {
        return new CrearProductoCommand("Pantalla LED", "pieza", "Anuncio full color", null, null, null, null);
    }

    @Test
    @DisplayName("crearProducto persiste el Producto activo y audita (Req 59.1, 59.8)")
    void crearProductoExitoso() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoDto dto = servicio.crearProducto(comandoValido());

        assertThat(dto.nombre()).isEqualTo("Pantalla LED");
        assertThat(dto.activo()).isTrue();
        verify(productoRepository).save(any(Producto.class));
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear");
        assertThat(ev.getValue().recurso()).isEqualTo("producto");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("crearProducto rechaza datos obligatorios ausentes con 422 y no persiste (Req 59.2)")
    void crearProductoDatosInvalidos() {
        CrearProductoCommand invalido = new CrearProductoCommand("Pantalla", "pieza", "  ", null, null, null, null);

        assertThatThrownBy(() -> servicio.crearProducto(invalido))
                .isInstanceOf(ReglaNegocioException.class);

        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("desactivarProducto realiza el borrado logico y audita (Req 59.6)")
    void desactivarProductoAudita() {
        Producto existente = Producto.crear("Pantalla", "pieza", "desc", null, null, null, null, "ventas");
        when(productoRepository.findByIdAndActivoTrue(existente.getId())).thenReturn(Optional.of(existente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoDto dto = servicio.desactivarProducto(existente.getId());

        assertThat(dto.activo()).isFalse();
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("eliminar");
    }

    @Test
    @DisplayName("consultarProducto de otro tenant devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void consultarProductoAccesoCruzado() {
        UUID ajeno = UUID.randomUUID();
        when(productoRepository.findByIdAndActivoTrue(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarProducto(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("producto");
    }

    @Test
    @DisplayName("listarProductos normaliza el filtro a minusculas y proyecta a DTO (Req 59.7)")
    void listarProductosFiltra() {
        Producto p = Producto.crear("Pantalla", "pieza", "desc", null, null, null, null, "ventas");
        Pageable pageable = PageRequest.of(0, 20);
        Page<Producto> pagina = new PageImpl<>(List.of(p), pageable, 1);
        when(productoRepository.buscarActivosPorNombre(eq("pantalla"), any(Pageable.class)))
                .thenReturn(pagina);

        Page<ProductoDto> resultado = servicio.listarProductos("  PANTALLA  ", pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).nombre()).isEqualTo("Pantalla");
        verify(productoRepository).buscarActivosPorNombre(eq("pantalla"), any(Pageable.class));
    }

    @Test
    @DisplayName("crearProducto persiste y devuelve la foto proporcionada (Req 59)")
    void crearProductoConFotoPersisteYDevuelve() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        CrearProductoCommand conFoto = new CrearProductoCommand(
                "Pantalla LED", "pieza", "Anuncio full color", null, null, null,
                "https://cdn.example.com/pantalla.png");

        ProductoDto dto = servicio.crearProducto(conFoto);

        assertThat(dto.foto()).isEqualTo("https://cdn.example.com/pantalla.png");
    }

    @Test
    @DisplayName("actualizarProducto cambia la foto del Producto activo (Req 59)")
    void actualizarProductoCambiaFoto() {
        Producto existente = Producto.crear("Pantalla", "pieza", "desc", null, null, null,
                "https://cdn.example.com/uno.png", "ventas");
        when(productoRepository.findByIdAndActivoTrue(existente.getId())).thenReturn(Optional.of(existente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoDto dto = servicio.actualizarProducto(existente.getId(), new ActualizarProductoCommand(
                "Pantalla", "pieza", "desc", null, null, null, "https://cdn.example.com/dos.png"));

        assertThat(dto.foto()).isEqualTo("https://cdn.example.com/dos.png");
    }

    @Test
    @DisplayName("actualizarProducto con foto en blanco la limpia (null) (Req 59)")
    void actualizarProductoLimpiaFoto() {
        Producto existente = Producto.crear("Pantalla", "pieza", "desc", null, null, null,
                "https://cdn.example.com/uno.png", "ventas");
        when(productoRepository.findByIdAndActivoTrue(existente.getId())).thenReturn(Optional.of(existente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoDto dto = servicio.actualizarProducto(existente.getId(), new ActualizarProductoCommand(
                "Pantalla", "pieza", "desc", null, null, null, "   "));

        assertThat(dto.foto()).isNull();
    }
}
