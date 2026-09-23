package com.dessti.crm.vertical.anuncios.levantamiento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dessti.crm.vertical.anuncios.levantamiento.adapter.out.persistence.LevantamientoFotoRepository;
import com.dessti.crm.vertical.anuncios.levantamiento.adapter.out.persistence.LevantamientoSitioRepository;
import com.dessti.crm.vertical.anuncios.levantamiento.domain.EstadoLevantamiento;
import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoFoto;
import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoSitio;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioLevantamientos} (Req 16). Usan dobles de
 * Mockito; no arrancan Spring ni base de datos. Cubren: creacion exitosa
 * (en_proceso + auditoria), validacion de obligatorios (422), verificacion de
 * vinculos opcionales (404 cross-tenant), adjuntar fotografias, completar (marca
 * UTC + actor + auditoria del cambio), completar un ya completado (409), consulta
 * 404 cross-tenant y listado con/sin filtros.
 */
class ServicioLevantamientosTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SITIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COTIZACION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDEN = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2024-05-01T12:00:00Z"), ZoneOffset.UTC);

    private LevantamientoSitioRepository repositorio;
    private LevantamientoFotoRepository fotoRepositorio;
    private EnlacesLevantamientoPort enlaces;
    private AuditoriaPort auditoria;
    private ServicioLevantamientos servicio;

    @BeforeEach
    void setUp() {
        repositorio = mock(LevantamientoSitioRepository.class);
        fotoRepositorio = mock(LevantamientoFotoRepository.class);
        enlaces = mock(EnlacesLevantamientoPort.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioLevantamientos(repositorio, fotoRepositorio, enlaces, auditoria, RELOJ);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CrearLevantamientoCommand comandoCompleto() {
        return new CrearLevantamientoCommand("3x2 m", "muro", "220V", SITIO, COTIZACION, ORDEN);
    }

    @Test
    @DisplayName("crear con datos y vinculos existentes crea el Levantamiento en_proceso y audita (Req 16.1, 16.7)")
    void crearExitoso() {
        when(enlaces.existeCotizacion(COTIZACION)).thenReturn(true);
        when(enlaces.existeOrdenFabricacion(ORDEN)).thenReturn(true);
        when(repositorio.save(any(LevantamientoSitio.class))).thenAnswer(inv -> inv.getArgument(0));

        LevantamientoSitioDto dto = servicio.crear(comandoCompleto());

        assertThat(dto.estado()).isEqualTo("en_proceso");
        assertThat(dto.sitioId()).isEqualTo(SITIO);
        assertThat(dto.cotizacionId()).isEqualTo(COTIZACION);
        assertThat(dto.ordenFabricacionId()).isEqualTo(ORDEN);
        assertThat(dto.id()).isNotNull();

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear");
        assertThat(ev.getValue().recurso()).isEqualTo("levantamiento_sitio");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
        assertThat(ev.getValue().valorNuevo()).isEqualTo("en_proceso");
    }

    @Test
    @DisplayName("crear sin vinculos no consulta el puerto de enlaces y persiste (Req 16.2)")
    void crearSinVinculos() {
        when(repositorio.save(any(LevantamientoSitio.class))).thenAnswer(inv -> inv.getArgument(0));

        LevantamientoSitioDto dto = servicio.crear(
                new CrearLevantamientoCommand("m", "muro", "220V", null, null, null));

        assertThat(dto.estado()).isEqualTo("en_proceso");
        verify(enlaces, never()).existeCotizacion(any());
        verify(enlaces, never()).existeOrdenFabricacion(any());
    }

    @Test
    @DisplayName("crear rechaza dato obligatorio faltante con 422 (Req 16.1)")
    void crearSinObligatorios() {
        assertThatThrownBy(() -> servicio.crear(
                new CrearLevantamientoCommand("  ", "muro", "220V", null, null, null)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear con Cotizacion inexistente devuelve 404 y audita el acceso cruzado (Req 16.2, 23.3)")
    void crearCotizacionInexistente() {
        when(enlaces.existeCotizacion(COTIZACION)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crear(comandoCompleto()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cotizacion");
    }

    @Test
    @DisplayName("crear con Orden_Fabricacion inexistente devuelve 404 (Req 16.2, 23.3)")
    void crearOrdenInexistente() {
        when(enlaces.existeCotizacion(COTIZACION)).thenReturn(true);
        when(enlaces.existeOrdenFabricacion(ORDEN)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crear(comandoCompleto()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("agregarFotos adjunta las referencias y audita (Req 16.3, 16.7)")
    void agregarFotos() {
        LevantamientoSitio l = LevantamientoSitio.crear("m", "muro", "220V", SITIO, null, null, "instalacion");
        when(repositorio.findById(l.getId())).thenReturn(Optional.of(l));
        when(fotoRepositorio.save(any(LevantamientoFoto.class))).thenAnswer(inv -> inv.getArgument(0));

        List<LevantamientoFotoDto> fotos =
                servicio.agregarFotos(l.getId(), List.of("http://a/1.jpg", "http://a/2.jpg"));

        assertThat(fotos).hasSize(2);
        assertThat(fotos.get(0).levantamientoId()).isEqualTo(l.getId());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("agregar_fotos");
    }

    @Test
    @DisplayName("agregarFotos sin referencias devuelve 422 (Req 16.3)")
    void agregarFotosVacio() {
        assertThatThrownBy(() -> servicio.agregarFotos(UUID.randomUUID(), List.of()))
                .isInstanceOf(ReglaNegocioException.class);
        verify(fotoRepositorio, never()).save(any());
    }

    @Test
    @DisplayName("completar marca completado con actor y marca UTC y audita el cambio (Req 16.4, 16.7)")
    void completar() {
        LevantamientoSitio l = LevantamientoSitio.crear("m", "muro", "220V", SITIO, null, null, "instalacion");
        when(repositorio.findById(l.getId())).thenReturn(Optional.of(l));
        when(repositorio.save(any(LevantamientoSitio.class))).thenAnswer(inv -> inv.getArgument(0));

        LevantamientoSitioDto dto = servicio.completar(l.getId());

        assertThat(dto.estado()).isEqualTo("completado");
        assertThat(dto.completadoEn()).isEqualTo(Instant.parse("2024-05-01T12:00:00Z"));
        assertThat(dto.completadoPor()).isNotBlank();
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("completar");
        assertThat(ev.getValue().valorAnterior()).isEqualTo("en_proceso");
        assertThat(ev.getValue().valorNuevo()).isEqualTo("completado");
    }

    @Test
    @DisplayName("completar un Levantamiento ya completado propaga 409 (Req 16.4)")
    void completarYaCompletado() {
        LevantamientoSitio l = LevantamientoSitio.crear("m", "muro", "220V", SITIO, null, null, "instalacion");
        l.completar("supervisor", RELOJ);
        when(repositorio.findById(l.getId())).thenReturn(Optional.of(l));

        assertThatThrownBy(() -> servicio.completar(l.getId()))
                .isInstanceOf(TransicionInvalidaException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("consultar un Levantamiento de otro tenant devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void consultarCrossTenant() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("levantamiento_sitio");
    }

    @Test
    @DisplayName("consultarDetalle devuelve el DTO enriquecido con las fotos vinculadas (Req 7.1, 7.2)")
    void consultarDetalleConFotos() {
        LevantamientoSitio l = LevantamientoSitio.crear("m", "muro", "220V", SITIO, null, null, "instalacion");
        when(repositorio.findById(l.getId())).thenReturn(Optional.of(l));
        LevantamientoFoto foto = LevantamientoFoto.paraLevantamiento(l, "http://a/1.jpg", "instalacion");
        when(fotoRepositorio.findByLevantamientoIdOrderByCreatedAtAsc(l.getId()))
                .thenReturn(List.of(foto));

        LevantamientoSitioDetalleDto dto = servicio.consultarDetalle(l.getId());

        assertThat(dto.id()).isEqualTo(l.getId());
        assertThat(dto.fotos()).hasSize(1);
        assertThat(dto.fotos().get(0).referencia()).isEqualTo("http://a/1.jpg");
    }

    @Test
    @DisplayName("consultarDetalle devuelve lista de fotos vacia cuando no hay (Req 7.2)")
    void consultarDetalleSinFotos() {
        LevantamientoSitio l = LevantamientoSitio.crear("m", "muro", "220V", SITIO, null, null, "instalacion");
        when(repositorio.findById(l.getId())).thenReturn(Optional.of(l));
        when(fotoRepositorio.findByLevantamientoIdOrderByCreatedAtAsc(l.getId()))
                .thenReturn(List.of());

        LevantamientoSitioDetalleDto dto = servicio.consultarDetalle(l.getId());

        assertThat(dto.fotos()).isEmpty();
    }

    @Test
    @DisplayName("fotosDe devuelve las fotos del Levantamiento accesible (Req 7.2)")
    void fotosDeAccesible() {
        LevantamientoSitio l = LevantamientoSitio.crear("m", "muro", "220V", SITIO, null, null, "instalacion");
        when(repositorio.findById(l.getId())).thenReturn(Optional.of(l));
        LevantamientoFoto foto = LevantamientoFoto.paraLevantamiento(l, "http://a/1.jpg", "instalacion");
        when(fotoRepositorio.findByLevantamientoIdOrderByCreatedAtAsc(l.getId()))
                .thenReturn(List.of(foto));

        List<LevantamientoFotoDto> fotos = servicio.fotosDe(l.getId());

        assertThat(fotos).hasSize(1);
        assertThat(fotos.get(0).levantamientoId()).isEqualTo(l.getId());
    }

    @Test
    @DisplayName("fotosDe devuelve lista vacia cuando el Levantamiento no tiene fotos (Req 7.2)")
    void fotosDeVacio() {
        LevantamientoSitio l = LevantamientoSitio.crear("m", "muro", "220V", SITIO, null, null, "instalacion");
        when(repositorio.findById(l.getId())).thenReturn(Optional.of(l));
        when(fotoRepositorio.findByLevantamientoIdOrderByCreatedAtAsc(l.getId()))
                .thenReturn(List.of());

        assertThat(servicio.fotosDe(l.getId())).isEmpty();
    }

    @Test
    @DisplayName("fotosDe de un Levantamiento no accesible devuelve 404 y audita el acceso cruzado (Req 7.3, 23.3)")
    void fotosDeCrossTenant() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.fotosDe(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("levantamiento_sitio");
        verify(fotoRepositorio, never()).findByLevantamientoIdOrderByCreatedAtAsc(any());
    }

    @Test
    @DisplayName("listar filtra por estado y por Sitio y devuelve resultados paginados (Req 16.6)")
    void listarConFiltros() {
        LevantamientoSitio l = LevantamientoSitio.crear("m", "muro", "220V", SITIO, null, null, "instalacion");
        Pageable pageable = PageRequest.of(0, 20);
        Page<LevantamientoSitio> pagina = new PageImpl<>(List.of(l), pageable, 1);
        when(repositorio.buscarConFiltros(
                eq(EstadoLevantamiento.EN_PROCESO), eq(SITIO), any(Pageable.class)))
                .thenReturn(pagina);

        Page<LevantamientoSitioDto> resultado = servicio.listar("en_proceso", SITIO, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).sitioId()).isEqualTo(SITIO);
    }

    @Test
    @DisplayName("listar sin filtros pasa nulos al repositorio (Req 16.6)")
    void listarSinFiltros() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repositorio.buscarConFiltros(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<LevantamientoSitioDto> resultado = servicio.listar(null, null, pageable);

        assertThat(resultado.getTotalElements()).isZero();
        verify(repositorio).buscarConFiltros(eq(null), eq(null), any(Pageable.class));
    }
}
