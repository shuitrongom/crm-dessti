package com.dessti.crm.vertical.anuncios.permiso.application;

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
import java.time.LocalDate;
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

import com.dessti.crm.vertical.anuncios.permiso.adapter.out.persistence.PermisoInstalacionRepository;
import com.dessti.crm.vertical.anuncios.permiso.domain.EstadoPermisoInstalacion;
import com.dessti.crm.vertical.anuncios.permiso.domain.PermisoInstalacion;
import com.dessti.crm.vertical.anuncios.permiso.domain.TipoPermisoInstalacion;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioPermisos} (Req 17). Usan dobles de Mockito; no
 * arrancan Spring ni base de datos. Cubren: creacion exitosa (solicitado +
 * auditoria), validacion de obligatorios (422), aprobar/rechazar validos (marca UTC
 * + actor + auditoria del cambio), transiciones invalidas desde final (409), consulta
 * 404 cross-tenant, listado con/sin filtros y la notificacion de vencimientos
 * proximos (dispara el notificador con un Clock fijo, Req 17.5).
 */
class ServicioPermisosTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SITIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant AHORA = Instant.parse("2024-05-01T12:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final LocalDate HOY = LocalDate.of(2024, 5, 1);
    private static final LocalDate VENCE = LocalDate.of(2025, 5, 1);

    private PermisoInstalacionRepository repositorio;
    private NotificadorPermisoPort notificador;
    private AuditoriaPort auditoria;
    private ServicioPermisos servicio;

    @BeforeEach
    void setUp() {
        repositorio = mock(PermisoInstalacionRepository.class);
        notificador = mock(NotificadorPermisoPort.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioPermisos(repositorio, notificador, auditoria, RELOJ);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CrearPermisoInstalacionCommand comando() {
        return new CrearPermisoInstalacionCommand("municipal", VENCE, SITIO);
    }

    @Test
    @DisplayName("crear con datos validos crea el permiso solicitado y audita (Req 17.1, 17.7)")
    void crearExitoso() {
        when(repositorio.save(any(PermisoInstalacion.class))).thenAnswer(inv -> inv.getArgument(0));

        PermisoInstalacionDto dto = servicio.crear(comando());

        assertThat(dto.estado()).isEqualTo("solicitado");
        assertThat(dto.tipo()).isEqualTo("municipal");
        assertThat(dto.fechaVencimiento()).isEqualTo(VENCE);
        assertThat(dto.sitioId()).isEqualTo(SITIO);
        assertThat(dto.id()).isNotNull();

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear");
        assertThat(ev.getValue().recurso()).isEqualTo("permiso_instalacion");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
        assertThat(ev.getValue().valorNuevo()).isEqualTo("solicitado");
    }

    @Test
    @DisplayName("crear rechaza tipo desconocido con 422 (Req 17.1)")
    void crearTipoDesconocido() {
        assertThatThrownBy(() -> servicio.crear(
                new CrearPermisoInstalacionCommand("comercial", VENCE, SITIO)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("crear rechaza fecha de vencimiento faltante con 422 (Req 17.1)")
    void crearSinFecha() {
        assertThatThrownBy(() -> servicio.crear(
                new CrearPermisoInstalacionCommand("municipal", null, SITIO)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("aprobar aplica solicitado->aprobado con actor/UTC y audita el cambio (Req 17.2, 17.7)")
    void aprobar() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, VENCE, SITIO, "instalacion");
        when(repositorio.findById(p.getId())).thenReturn(Optional.of(p));
        when(repositorio.save(any(PermisoInstalacion.class))).thenAnswer(inv -> inv.getArgument(0));

        PermisoInstalacionDto dto = servicio.aprobar(p.getId());

        assertThat(dto.estado()).isEqualTo("aprobado");
        assertThat(dto.decididoEn()).isEqualTo(AHORA);
        assertThat(dto.decididoPor()).isNotBlank();
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("cambiar_estado");
        assertThat(ev.getValue().valorAnterior()).isEqualTo("solicitado");
        assertThat(ev.getValue().valorNuevo()).isEqualTo("aprobado");
    }

    @Test
    @DisplayName("rechazar aplica solicitado->rechazado y audita el cambio (Req 17.2, 17.7)")
    void rechazar() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.ARRENDADOR, VENCE, SITIO, "instalacion");
        when(repositorio.findById(p.getId())).thenReturn(Optional.of(p));
        when(repositorio.save(any(PermisoInstalacion.class))).thenAnswer(inv -> inv.getArgument(0));

        PermisoInstalacionDto dto = servicio.rechazar(p.getId());

        assertThat(dto.estado()).isEqualTo("rechazado");
        assertThat(dto.decididoPor()).isNotBlank();
    }

    @Test
    @DisplayName("aprobar un permiso ya aprobado propaga 409 y no persiste (Req 17.3)")
    void aprobarYaFinal() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, VENCE, SITIO, "instalacion");
        p.aprobar("supervisor", RELOJ);
        when(repositorio.findById(p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> servicio.aprobar(p.getId()))
                .isInstanceOf(TransicionInvalidaException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("consultar un permiso de otro tenant devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void consultarCrossTenant() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("permiso_instalacion");
    }

    @Test
    @DisplayName("listar filtra por Sitio, tipo y estado y devuelve resultados paginados (Req 17.6)")
    void listarConFiltros() {
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, VENCE, SITIO, "instalacion");
        Pageable pageable = PageRequest.of(0, 20);
        Page<PermisoInstalacion> pagina = new PageImpl<>(List.of(p), pageable, 1);
        when(repositorio.buscarConFiltros(
                eq(SITIO), eq(TipoPermisoInstalacion.MUNICIPAL),
                eq(EstadoPermisoInstalacion.SOLICITADO), any(Pageable.class)))
                .thenReturn(pagina);

        Page<PermisoInstalacionDto> resultado =
                servicio.listar(SITIO, "municipal", "solicitado", pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).sitioId()).isEqualTo(SITIO);
    }

    @Test
    @DisplayName("listar sin filtros pasa nulos al repositorio (Req 17.6)")
    void listarSinFiltros() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repositorio.buscarConFiltros(eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<PermisoInstalacionDto> resultado = servicio.listar(null, null, null, pageable);

        assertThat(resultado.getTotalElements()).isZero();
        verify(repositorio).buscarConFiltros(eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("notificarVencimientosProximos dispara el notificador por cada permiso por vencer (Req 17.5)")
    void notificarVencimientos() {
        // Permiso aprobado que vence a 20 dias de HOY (dentro de la ventana de 30).
        PermisoInstalacion p = PermisoInstalacion.crear(
                TipoPermisoInstalacion.MUNICIPAL, LocalDate.of(2024, 5, 21), SITIO, "instalacion");
        p.aprobar("supervisor", RELOJ);
        // La consulta se acota a [hoy, hoy+30] por el servicio con el Clock fijo.
        when(repositorio.buscarAprobadosVenciendoEntre(
                eq(HOY), eq(HOY.plusDays(30)))).thenReturn(List.of(p));

        int notificados = servicio.notificarVencimientosProximos();

        assertThat(notificados).isEqualTo(1);
        ArgumentCaptor<NotificacionVencimientoPermiso> not =
                ArgumentCaptor.forClass(NotificacionVencimientoPermiso.class);
        verify(notificador).notificarVencimientoProximo(not.capture());
        assertThat(not.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(not.getValue().sitioId()).isEqualTo(SITIO);
        assertThat(not.getValue().tipo()).isEqualTo("municipal");
        assertThat(not.getValue().fechaVencimiento()).isEqualTo(LocalDate.of(2024, 5, 21));
        assertThat(not.getValue().diasParaVencer()).isEqualTo(20L);
    }

    @Test
    @DisplayName("notificarVencimientosProximos no dispara nada si no hay permisos por vencer (Req 17.5)")
    void notificarVencimientosVacio() {
        when(repositorio.buscarAprobadosVenciendoEntre(any(), any())).thenReturn(List.of());

        int notificados = servicio.notificarVencimientosProximos();

        assertThat(notificados).isZero();
        verify(notificador, never()).notificarVencimientoProximo(any());
    }
}
