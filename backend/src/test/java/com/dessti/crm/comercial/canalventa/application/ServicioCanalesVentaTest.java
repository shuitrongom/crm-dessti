package com.dessti.crm.comercial.canalventa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.dessti.crm.comercial.canalventa.adapter.out.persistence.CanalVentaRepository;
import com.dessti.crm.comercial.canalventa.domain.CanalVenta;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioCanalesVenta} (Req 63.1). Usan dobles de
 * Mockito; no arrancan Spring ni base de datos. Cubren alta con verificacion de
 * unicidad de nombre (409), rechazo de dato invalido (422), actualizacion, baja
 * logica, consulta con acceso cruzado (404 + auditoria, 23.3) y listado.
 */
class ServicioCanalesVentaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private CanalVentaRepository repositorio;
    private AuditoriaPort auditoria;
    private ServicioCanalesVenta servicio;

    @BeforeEach
    void setUp() {
        repositorio = mock(CanalVentaRepository.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioCanalesVenta(repositorio, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("crearCanal valida el nombre, comprueba unicidad, persiste y audita (Req 63.1)")
    void crearExitoso() {
        when(repositorio.existePorNombreActivo(eq("directo"))).thenReturn(false);
        when(repositorio.saveAndFlush(any(CanalVenta.class))).thenAnswer(inv -> inv.getArgument(0));

        CanalVentaDto dto = servicio.crearCanal(new CrearCanalVentaCommand("Directo", "Venta directa"));

        assertThat(dto.nombre()).isEqualTo("Directo");
        assertThat(dto.activo()).isTrue();
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear");
        assertThat(ev.getValue().recurso()).isEqualTo("canal_venta");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("crearCanal rechaza el nombre duplicado entre activos con 409 (Req 23.6)")
    void crearDuplicado() {
        when(repositorio.existePorNombreActivo(eq("directo"))).thenReturn(true);

        assertThatThrownBy(() -> servicio.crearCanal(new CrearCanalVentaCommand("Directo", null)))
                .isInstanceOf(ConflictoUnicidadException.class);

        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("crearCanal traduce la violacion del indice unico a 409 ante carreras (Req 23.6)")
    void crearCarreraConcurrente() {
        when(repositorio.existePorNombreActivo(anyString())).thenReturn(false);
        when(repositorio.saveAndFlush(any(CanalVenta.class)))
                .thenThrow(new DataIntegrityViolationException("uq_canal_venta_nombre_activo_por_tenant"));

        assertThatThrownBy(() -> servicio.crearCanal(new CrearCanalVentaCommand("Directo", null)))
                .isInstanceOf(ConflictoUnicidadException.class);
    }

    @Test
    @DisplayName("crearCanal con nombre invalido devuelve 422 antes de comprobar unicidad (Req 63.1)")
    void crearNombreInvalido() {
        assertThatThrownBy(() -> servicio.crearCanal(new CrearCanalVentaCommand("   ", null)))
                .isInstanceOf(ReglaNegocioException.class);

        verify(repositorio, never()).existePorNombreActivo(anyString());
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("actualizarCanal aplica los cambios cuando el canal existe (Req 63.1)")
    void actualizarExitoso() {
        CanalVenta canal = CanalVenta.crear("Directo", "desc", "ventas");
        when(repositorio.findByIdAndActivoTrue(canal.getId())).thenReturn(Optional.of(canal));
        when(repositorio.existePorNombreActivo(eq("referido"))).thenReturn(false);
        when(repositorio.saveAndFlush(any(CanalVenta.class))).thenAnswer(inv -> inv.getArgument(0));

        CanalVentaDto dto = servicio.actualizarCanal(canal.getId(),
                new ActualizarCanalVentaCommand("Referido", "Por recomendacion"));

        assertThat(dto.nombre()).isEqualTo("Referido");
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("actualizar");
    }

    @Test
    @DisplayName("actualizarCanal de otro tenant devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void actualizarAccesoCruzado() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findByIdAndActivoTrue(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizarCanal(ajeno,
                new ActualizarCanalVentaCommand("Nuevo", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("canal_venta");
    }

    @Test
    @DisplayName("desactivarCanal realiza la baja logica y audita (Req 63.1)")
    void desactivarExitoso() {
        CanalVenta canal = CanalVenta.crear("Directo", "desc", "ventas");
        when(repositorio.findByIdAndActivoTrue(canal.getId())).thenReturn(Optional.of(canal));
        when(repositorio.save(any(CanalVenta.class))).thenAnswer(inv -> inv.getArgument(0));

        CanalVentaDto dto = servicio.desactivarCanal(canal.getId());

        assertThat(dto.activo()).isFalse();
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("eliminar");
    }

    @Test
    @DisplayName("consultarCanal de otro tenant devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void consultarAccesoCruzado() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findByIdAndActivoTrue(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarCanal(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
    }

    @Test
    @DisplayName("listarCanales proyecta a DTO y filtra en minusculas (Req 63.1)")
    void listar() {
        CanalVenta canal = CanalVenta.crear("Directo", "desc", "ventas");
        Pageable pageable = PageRequest.of(0, 20);
        Page<CanalVenta> pagina = new PageImpl<>(List.of(canal), pageable, 1);
        when(repositorio.buscarActivosPorNombre(eq("dir"), any(Pageable.class))).thenReturn(pagina);

        Page<CanalVentaDto> resultado = servicio.listarCanales("DIR", pageable);

        assertThat(resultado.getContent()).hasSize(1);
        verify(repositorio).buscarActivosPorNombre(eq("dir"), any(Pageable.class));
    }
}
