package com.dessti.crm.operacion.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;
import com.dessti.crm.operacion.inventario.adapter.out.persistence.MovimientoInventarioRepository;
import com.dessti.crm.operacion.inventario.domain.Material;
import com.dessti.crm.operacion.inventario.domain.MovimientoInventario;
import com.dessti.crm.operacion.inventario.domain.TipoMovimientoInventario;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioInventario} (Req 18). Usan dobles de Mockito; no
 * arrancan Spring ni base de datos. Cubren: alta con existencias 0 y auditoria (18.1,
 * 18.7); movimientos entrada/salida/ajuste (18.2); rechazo de salida insuficiente con
 * 422 y sin persistir el movimiento (18.3, Property 9); notificacion de stock bajo
 * (18.5); consumo por Orden_Fabricacion (18.4); 404 cross-tenant (23.3); y listado con
 * filtros (18.6).
 */
class ServicioInventarioTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MATERIAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private MaterialRepository materialRepository;
    private MovimientoInventarioRepository movimientoRepository;
    private NotificadorStockPort notificadorStock;
    private AuditoriaPort auditoria;
    private ServicioInventario servicio;

    @BeforeEach
    void setUp() {
        materialRepository = mock(MaterialRepository.class);
        movimientoRepository = mock(MovimientoInventarioRepository.class);
        notificadorStock = mock(NotificadorStockPort.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioInventario(
                materialRepository, movimientoRepository, notificadorStock, auditoria);
        TenantContext.set(TENANT);
        when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoRepository.save(any(MovimientoInventario.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Crea un Material real con existencias iniciales aplicando una entrada. */
    private Material materialConExistencias(BigDecimal existencias, BigDecimal stockMinimo) {
        Material material = Material.crear("Perfil", "metro", stockMinimo, "almacen");
        if (existencias.signum() > 0) {
            material.aplicarMovimiento(TipoMovimientoInventario.ENTRADA, existencias, "almacen");
        }
        return material;
    }

    @Test
    @DisplayName("crearMaterial da de alta con existencias 0 y audita el alta (Req 18.1, 18.7)")
    void crearMaterial() {
        MaterialDto dto = servicio.crearMaterial("Tubo LED", "pieza", new BigDecimal("5"));

        assertThat(dto.existencias()).isEqualByComparingTo("0");
        assertThat(dto.nombre()).isEqualTo("Tubo LED");
        assertThat(dto.activo()).isTrue();

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear");
        assertThat(ev.getValue().recurso()).isEqualTo("material");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("registrarMovimiento entrada incrementa existencias, agrega historial y audita (Req 18.2, 18.7)")
    void registrarEntrada() {
        Material material = materialConExistencias(BigDecimal.ZERO, BigDecimal.ZERO);
        when(materialRepository.findByIdAndActivoTrue(material.getId()))
                .thenReturn(Optional.of(material));

        MovimientoInventarioDto dto =
                servicio.registrarMovimiento(material.getId(), "entrada", new BigDecimal("10"), null);

        assertThat(dto.tipo()).isEqualTo("entrada");
        assertThat(dto.existenciasResultantes()).isEqualByComparingTo("10");
        assertThat(material.getExistencias()).isEqualByComparingTo("10");
        verify(movimientoRepository).save(any(MovimientoInventario.class));

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("registrar_movimiento");
        assertThat(ev.getValue().recurso()).isEqualTo("movimiento_inventario");
    }

    @Test
    @DisplayName("registrarMovimiento salida decrementa existencias (Req 18.2)")
    void registrarSalida() {
        Material material = materialConExistencias(new BigDecimal("10"), BigDecimal.ZERO);
        when(materialRepository.findByIdAndActivoTrue(material.getId()))
                .thenReturn(Optional.of(material));

        MovimientoInventarioDto dto =
                servicio.registrarMovimiento(material.getId(), "salida", new BigDecimal("4"), null);

        assertThat(dto.existenciasResultantes()).isEqualByComparingTo("6");
        assertThat(material.getExistencias()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("registrarMovimiento salida insuficiente devuelve 422 y NO agrega movimiento (Req 18.3, Property 9)")
    void registrarSalidaInsuficiente() {
        Material material = materialConExistencias(new BigDecimal("3"), BigDecimal.ZERO);
        when(materialRepository.findByIdAndActivoTrue(material.getId()))
                .thenReturn(Optional.of(material));

        assertThatThrownBy(() ->
                servicio.registrarMovimiento(material.getId(), "salida", new BigDecimal("5"), null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("existencias insuficientes");

        assertThat(material.getExistencias()).isEqualByComparingTo("3");
        verify(movimientoRepository, never()).save(any());
        verify(notificadorStock, never()).notificarStockBajo(any());
    }

    @Test
    @DisplayName("registrarMovimiento notifica stock bajo al cruzar por debajo del stock minimo (Req 18.5)")
    void registrarNotificaStockBajo() {
        Material material = materialConExistencias(new BigDecimal("10"), new BigDecimal("8"));
        when(materialRepository.findByIdAndActivoTrue(material.getId()))
                .thenReturn(Optional.of(material));

        servicio.registrarMovimiento(material.getId(), "salida", new BigDecimal("3"), null);

        ArgumentCaptor<NotificacionStockBajo> notif =
                ArgumentCaptor.forClass(NotificacionStockBajo.class);
        verify(notificadorStock).notificarStockBajo(notif.capture());
        assertThat(notif.getValue().materialId()).isEqualTo(material.getId());
        assertThat(notif.getValue().existencias()).isEqualByComparingTo("7");
        assertThat(notif.getValue().stockMinimo()).isEqualByComparingTo("8");
        assertThat(notif.getValue().tenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("registrarMovimiento no notifica cuando el saldo queda en/por encima del minimo (Req 18.5)")
    void registrarNoNotificaCuandoSuficiente() {
        Material material = materialConExistencias(new BigDecimal("10"), new BigDecimal("5"));
        when(materialRepository.findByIdAndActivoTrue(material.getId()))
                .thenReturn(Optional.of(material));

        servicio.registrarMovimiento(material.getId(), "salida", new BigDecimal("2"), null);

        verify(notificadorStock, never()).notificarStockBajo(any());
    }

    @Test
    @DisplayName("registrarMovimiento con tipo desconocido devuelve 422")
    void registrarTipoDesconocido() {
        assertThatThrownBy(() ->
                servicio.registrarMovimiento(MATERIAL_ID, "transferencia", new BigDecimal("1"), null))
                .isInstanceOf(ReglaNegocioException.class);
        verify(materialRepository, never()).findByIdAndActivoTrue(any());
    }

    @Test
    @DisplayName("registrarMovimiento sobre Material inexistente devuelve 404 y audita acceso cruzado (Req 23.3)")
    void registrarMaterialInexistente() {
        when(materialRepository.findByIdAndActivoTrue(MATERIAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                servicio.registrarMovimiento(MATERIAL_ID, "entrada", new BigDecimal("1"), null))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("material");
    }

    @Test
    @DisplayName("consumirParaOrdenFabricacion registra una salida por Material y descuenta (Req 18.4)")
    void consumirParaOrdenFabricacion() {
        Material material = materialConExistencias(new BigDecimal("10"), BigDecimal.ZERO);
        when(materialRepository.findByIdAndActivoTrue(material.getId()))
                .thenReturn(Optional.of(material));

        List<MovimientoInventarioDto> movimientos = servicio.consumirParaOrdenFabricacion(
                OF_ID, List.of(new ConsumoMaterial(material.getId(), new BigDecimal("4"))));

        assertThat(movimientos).hasSize(1);
        assertThat(movimientos.get(0).tipo()).isEqualTo("salida");
        assertThat(movimientos.get(0).ordenFabricacionId()).isEqualTo(OF_ID);
        assertThat(movimientos.get(0).existenciasResultantes()).isEqualByComparingTo("6");
        assertThat(material.getExistencias()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("consumirParaOrdenFabricacion rechaza consumo que dejaria existencias < 0 (Req 18.4, Property 9)")
    void consumirInsuficiente() {
        Material material = materialConExistencias(new BigDecimal("2"), BigDecimal.ZERO);
        when(materialRepository.findByIdAndActivoTrue(material.getId()))
                .thenReturn(Optional.of(material));

        assertThatThrownBy(() -> servicio.consumirParaOrdenFabricacion(
                OF_ID, List.of(new ConsumoMaterial(material.getId(), new BigDecimal("5")))))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("existencias insuficientes");
        assertThat(material.getExistencias()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("desactivar realiza la baja logica y audita (Req 18, 3.1)")
    void desactivar() {
        Material material = materialConExistencias(BigDecimal.ZERO, BigDecimal.ZERO);
        when(materialRepository.findByIdAndActivoTrue(material.getId()))
                .thenReturn(Optional.of(material));

        MaterialDto dto = servicio.desactivar(material.getId());

        assertThat(dto.activo()).isFalse();
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("eliminar");
    }

    @Test
    @DisplayName("desactivar un Material con existencias devuelve 422 y NO persiste ni audita (regla de negocio)")
    void desactivarConExistenciasRechaza() {
        Material material = materialConExistencias(new BigDecimal("7"), BigDecimal.ZERO);
        when(materialRepository.findByIdAndActivoTrue(material.getId()))
                .thenReturn(Optional.of(material));

        assertThatThrownBy(() -> servicio.desactivar(material.getId()))
                .isInstanceOf(ReglaNegocioException.class);

        // El dominio rechaza la baja antes de mutar: no se persiste el cambio.
        assertThat(material.isActivo()).isTrue();
        verify(materialRepository, never()).save(any(Material.class));
    }

    @Test
    @DisplayName("listarMateriales pasa el filtro por nombre y por stock bajo al repositorio (Req 18.6)")
    void listarConFiltros() {
        Material material = materialConExistencias(new BigDecimal("1"), new BigDecimal("5"));
        Pageable pageable = PageRequest.of(0, 20);
        Page<Material> pagina = new PageImpl<>(List.of(material), pageable, 1);
        when(materialRepository.buscarPorNombreEstadoYStock(
                eq("perfil"), eq(Boolean.TRUE), eq(true), any(Pageable.class)))
                .thenReturn(pagina);

        Page<MaterialDto> resultado =
                servicio.listarMateriales("perfil", Boolean.TRUE, true, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).stockBajo()).isTrue();
        verify(materialRepository).buscarPorNombreEstadoYStock(
                eq("perfil"), eq(Boolean.TRUE), eq(true), any(Pageable.class));
    }

    @Test
    @DisplayName("listarMateriales sin filtros pasa nombre nulo, estado nulo y stockBajo false (Req 18.6)")
    void listarSinFiltros() {
        Pageable pageable = PageRequest.of(0, 20);
        when(materialRepository.buscarPorNombreEstadoYStock(
                eq(null), eq(null), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<MaterialDto> resultado = servicio.listarMateriales("   ", null, false, pageable);

        assertThat(resultado.getTotalElements()).isZero();
        verify(materialRepository).buscarPorNombreEstadoYStock(
                eq(null), eq(null), eq(false), any(Pageable.class));
    }

    @Test
    @DisplayName("editar actualiza nombre/unidad/stockMinimo y audita (Req 18)")
    void editarMaterial() {
        Material material = materialConExistencias(new BigDecimal("2"), new BigDecimal("5"));
        when(materialRepository.findById(material.getId())).thenReturn(Optional.of(material));

        MaterialDto dto = servicio.editar(
                material.getId(), "Perfil reforzado", "pieza", new BigDecimal("8"));

        assertThat(dto.nombre()).isEqualTo("Perfil reforzado");
        assertThat(dto.unidadMedida()).isEqualTo("pieza");
        assertThat(dto.stockMinimo()).isEqualByComparingTo("8");
        // Las existencias no se tocan al editar.
        assertThat(dto.existencias()).isEqualByComparingTo("2");

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("actualizar");
        assertThat(ev.getValue().recurso()).isEqualTo("material");
    }

    @Test
    @DisplayName("editar lanza 404 cuando el Material no es accesible (Req 23.3)")
    void editarNoAccesible() {
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.editar(MATERIAL_ID, "X", "pieza", BigDecimal.ONE))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(materialRepository, never()).save(any(Material.class));
    }

    @Test
    @DisplayName("activar reactiva un Material dado de baja y audita (Req 18, 3.1)")
    void activarMaterial() {
        Material material = materialConExistencias(BigDecimal.ZERO, new BigDecimal("5"));
        material.desactivar("almacen");
        when(materialRepository.findById(material.getId())).thenReturn(Optional.of(material));

        MaterialDto dto = servicio.activar(material.getId());

        assertThat(dto.activo()).isTrue();
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("actualizar");
    }
}
