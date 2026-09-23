package com.dessti.crm.operacion.produccion.application;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterialPort;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.PartidaOrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.operacion.produccion.application.PruebaDisenoAprobadaPort;

/**
 * Pruebas unitarias de {@link ServicioOrdenesFabricacion} (Req 7, 15.5; Property 7).
 * Usan dobles de Mockito; no arrancan Spring ni base de datos. Cubren: generacion
 * exitosa (las tres precondiciones se cumplen -> OF pendiente y auditoria),
 * rechazos por cada precondicion (Cotizacion no aprobada 422, OF ya existente 409,
 * sin Prueba_Diseno aprobada 422), 404 por Cotizacion inexistente, doble defensa de
 * unicidad (DataIntegrityViolationException -> 409), cambio de estado con auditoria,
 * consulta 404 cross-tenant y listado con filtros.
 */
class ServicioOrdenesFabricacionTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COTIZACION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLIENTE = UUID.fromString("33333333-3333-3333-3333-333333333333");

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
        servicio = new ServicioOrdenesFabricacion(
                repositorio, partidaRepositorio, cotizacionParaFabricacion, pruebaDisenoAprobada,
                clienteExistente, materialAccesible, consumoMaterialPort, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void cotizacionAprobada() {
        when(cotizacionParaFabricacion.buscarParaFabricacion(COTIZACION))
                .thenReturn(Optional.of(
                        new CotizacionParaFabricacion(COTIZACION, "aprobada", CLIENTE)));
    }

    @Test
    @DisplayName("generar con las tres precondiciones crea la OF pendiente y audita (Req 7.1-7.4, 15.5)")
    void generarExitoso() {
        cotizacionAprobada();
        when(repositorio.existsByCotizacionId(COTIZACION)).thenReturn(false);
        when(pruebaDisenoAprobada.tieneAprobadaPorCotizacion(COTIZACION)).thenReturn(true);
        when(repositorio.saveAndFlush(any(OrdenFabricacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrdenFabricacionDto dto = servicio.generar(COTIZACION);

        assertThat(dto.estado()).isEqualTo("pendiente");
        assertThat(dto.cotizacionId()).isEqualTo(COTIZACION);
        assertThat(dto.clienteId()).isEqualTo(CLIENTE);
        assertThat(dto.id()).isNotNull();

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("generar");
        assertThat(ev.getValue().recurso()).isEqualTo("orden_fabricacion");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
        assertThat(ev.getValue().valorNuevo()).isEqualTo("pendiente");
    }

    @Test
    @DisplayName("generar con Cotizacion inexistente devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void generarCotizacionInexistente() {
        when(cotizacionParaFabricacion.buscarParaFabricacion(COTIZACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.generar(COTIZACION))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).saveAndFlush(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cotizacion");
    }

    @Test
    @DisplayName("generar rechaza si la Cotizacion no esta aprobada (422, Req 7.2)")
    void generarCotizacionNoAprobada() {
        when(cotizacionParaFabricacion.buscarParaFabricacion(COTIZACION))
                .thenReturn(Optional.of(
                        new CotizacionParaFabricacion(COTIZACION, "enviada", CLIENTE)));

        assertThatThrownBy(() -> servicio.generar(COTIZACION))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("se requiere una Cotizacion aprobada");

        verify(repositorio, never()).existsByCotizacionId(any());
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("generar rechaza si la Cotizacion ya tiene una OF (409, Req 7.3)")
    void generarConOfExistente() {
        cotizacionAprobada();
        when(repositorio.existsByCotizacionId(COTIZACION)).thenReturn(true);

        assertThatThrownBy(() -> servicio.generar(COTIZACION))
                .isInstanceOf(ConflictoUnicidadException.class)
                .hasMessageContaining("ya tiene una Orden_Fabricacion");

        verify(pruebaDisenoAprobada, never()).tieneAprobadaPorCotizacion(any());
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("generar rechaza si no hay Prueba_Diseno aprobada (422, Req 15.5)")
    void generarSinPruebaAprobada() {
        cotizacionAprobada();
        when(repositorio.existsByCotizacionId(COTIZACION)).thenReturn(false);
        when(pruebaDisenoAprobada.tieneAprobadaPorCotizacion(COTIZACION)).thenReturn(false);

        assertThatThrownBy(() -> servicio.generar(COTIZACION))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("se requiere una Prueba_Diseno aprobada");

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("generar traduce la violacion del indice unico a 409 (doble defensa, Req 7.3)")
    void generarCarreraConcurrenteUnicidad() {
        cotizacionAprobada();
        when(repositorio.existsByCotizacionId(COTIZACION)).thenReturn(false);
        when(pruebaDisenoAprobada.tieneAprobadaPorCotizacion(COTIZACION)).thenReturn(true);
        when(repositorio.saveAndFlush(any(OrdenFabricacion.class)))
                .thenThrow(new DataIntegrityViolationException("uq_orden_fabricacion_cotizacion"));

        assertThatThrownBy(() -> servicio.generar(COTIZACION))
                .isInstanceOf(ConflictoUnicidadException.class);
    }

    @Test
    @DisplayName("cambiarEstado aplica la maquina y audita anterior -> nuevo (Req 7.5, 7.10)")
    void cambiarEstado() {
        OrdenFabricacion orden = OrdenFabricacion.generar(COTIZACION, CLIENTE, "produccion");
        when(repositorio.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(repositorio.save(any(OrdenFabricacion.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenFabricacionDto dto = servicio.cambiarEstado(orden.getId(), "en_produccion");

        assertThat(dto.estado()).isEqualTo("en_produccion");
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("cambiar_estado");
        assertThat(ev.getValue().valorAnterior()).isEqualTo("pendiente");
        assertThat(ev.getValue().valorNuevo()).isEqualTo("en_produccion");
    }

    @Test
    @DisplayName("cambiarEstado con transicion invalida propaga 409 (Req 7.6)")
    void cambiarEstadoInvalido() {
        OrdenFabricacion orden = OrdenFabricacion.generar(COTIZACION, CLIENTE, "produccion");
        when(repositorio.findById(orden.getId())).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> servicio.cambiarEstado(orden.getId(), "terminada"))
                .isInstanceOf(TransicionInvalidaException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("cambiarEstado con etiqueta desconocida devuelve 422")
    void cambiarEstadoEtiquetaDesconocida() {
        assertThatThrownBy(() -> servicio.cambiarEstado(UUID.randomUUID(), "en_producción"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("consultar una OF de otro tenant devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void consultarCrossTenant() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("orden_fabricacion");
    }

    @Test
    @DisplayName("listar filtra por estado y por Cliente y devuelve resultados paginados (Req 7.9)")
    void listarConFiltros() {
        OrdenFabricacion orden = OrdenFabricacion.generar(COTIZACION, CLIENTE, "produccion");
        Pageable pageable = PageRequest.of(0, 20);
        Page<OrdenFabricacion> pagina = new PageImpl<>(List.of(orden), pageable, 1);
        when(repositorio.buscarConFiltros(
                eq(EstadoOrdenFabricacion.PENDIENTE), eq(CLIENTE), any(Pageable.class)))
                .thenReturn(pagina);

        Page<OrdenFabricacionDto> resultado = servicio.listar("pendiente", CLIENTE, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).clienteId()).isEqualTo(CLIENTE);
        verify(repositorio).buscarConFiltros(
                eq(EstadoOrdenFabricacion.PENDIENTE), eq(CLIENTE), any(Pageable.class));
    }

    @Test
    @DisplayName("listar sin filtros pasa nulos al repositorio (Req 7.9)")
    void listarSinFiltros() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repositorio.buscarConFiltros(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<OrdenFabricacionDto> resultado = servicio.listar(null, null, pageable);

        assertThat(resultado.getTotalElements()).isZero();
        verify(repositorio).buscarConFiltros(eq(null), eq(null), any(Pageable.class));
    }
}
