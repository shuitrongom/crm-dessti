package com.dessti.crm.vertical.anuncios.pruebadiseno.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.dessti.crm.vertical.anuncios.pruebadiseno.adapter.out.persistence.PruebaDisenoRepository;
import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.EstadoPruebaDiseno;
import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.PruebaDiseno;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioPruebasDiseno} (Req 15). Usan dobles de
 * Mockito; no arrancan Spring ni base de datos. Cubren: generacion de la version 1
 * pendiente (15.1), 404 si la Cotizacion no existe (15.1, 23.3), aprobacion con
 * marca UTC (15.2), rechazo que genera automaticamente la version max+1 pendiente
 * (15.3, Property 8), 409 al decidir una prueba ya decidida (15.4) y listado
 * paginado (15.6).
 */
class ServicioPruebasDisenoTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COTIZACION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2024-05-01T12:00:00Z"), ZoneOffset.UTC);

    private PruebaDisenoRepository repositorio;
    private CotizacionExistentePort cotizacionExistente;
    private AuditoriaPort auditoria;
    private ServicioPruebasDiseno servicio;

    @BeforeEach
    void setUp() {
        repositorio = mock(PruebaDisenoRepository.class);
        cotizacionExistente = mock(CotizacionExistentePort.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioPruebasDiseno(repositorio, cotizacionExistente, auditoria, RELOJ);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("generar verifica la Cotizacion, crea version 1 pendiente y audita (Req 15.1, 15.7)")
    void generarExitoso() {
        when(cotizacionExistente.existeCotizacion(COTIZACION)).thenReturn(true);
        when(repositorio.save(any(PruebaDiseno.class))).thenAnswer(inv -> inv.getArgument(0));

        PruebaDisenoDto dto = servicio.generar(COTIZACION);

        assertThat(dto.numeroVersion()).isEqualTo(1);
        assertThat(dto.estado()).isEqualTo("pendiente");
        assertThat(dto.cotizacionId()).isEqualTo(COTIZACION);
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("generar");
        assertThat(ev.getValue().recurso()).isEqualTo("prueba_diseno");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("generar con Cotizacion inexistente devuelve 404 y audita el acceso cruzado (Req 15.1, 23.3)")
    void generarCotizacionInexistente() {
        when(cotizacionExistente.existeCotizacion(COTIZACION)).thenReturn(false);

        assertThatThrownBy(() -> servicio.generar(COTIZACION))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cotizacion");
    }

    @Test
    @DisplayName("aprobar cambia a aprobada con marca UTC y audita anterior -> nuevo (Req 15.2, 15.7)")
    void aprobar() {
        PruebaDiseno prueba = PruebaDiseno.generarInicial(COTIZACION, "diseno");
        when(repositorio.findById(prueba.getId())).thenReturn(Optional.of(prueba));
        when(repositorio.save(any(PruebaDiseno.class))).thenAnswer(inv -> inv.getArgument(0));

        PruebaDisenoDto dto = servicio.aprobar(prueba.getId());

        assertThat(dto.estado()).isEqualTo("aprobada");
        assertThat(dto.decididaEn()).isEqualTo(Instant.parse("2024-05-01T12:00:00Z"));
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("aprobar");
        assertThat(ev.getValue().valorAnterior()).isEqualTo("pendiente");
        assertThat(ev.getValue().valorNuevo()).isEqualTo("aprobada");
    }

    @Test
    @DisplayName("aprobar una prueba ya decidida propaga 409 (Req 15.4)")
    void aprobarYaDecidida() {
        PruebaDiseno prueba = PruebaDiseno.generarInicial(COTIZACION, "diseno");
        prueba.aprobar("cliente", RELOJ);
        when(repositorio.findById(prueba.getId())).thenReturn(Optional.of(prueba));

        assertThatThrownBy(() -> servicio.aprobar(prueba.getId()))
                .isInstanceOf(TransicionInvalidaException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("rechazar cambia a rechazada y genera la version max+1 pendiente (Req 15.3, Property 8)")
    void rechazarGeneraSiguienteVersion() {
        PruebaDiseno v1 = PruebaDiseno.generarInicial(COTIZACION, "diseno");
        when(repositorio.findById(v1.getId())).thenReturn(Optional.of(v1));
        when(repositorio.save(any(PruebaDiseno.class))).thenAnswer(inv -> inv.getArgument(0));
        // El maximo actual de version para la Cotizacion es 1 -> la nueva sera 2.
        when(repositorio.findMaxNumeroVersionByCotizacionId(COTIZACION)).thenReturn(1);

        ResultadoRechazoPruebaDiseno resultado = servicio.rechazar(v1.getId());

        assertThat(resultado.rechazada().estado()).isEqualTo("rechazada");
        assertThat(resultado.rechazada().numeroVersion()).isEqualTo(1);
        assertThat(resultado.nuevaVersion().estado()).isEqualTo("pendiente");
        assertThat(resultado.nuevaVersion().numeroVersion()).isEqualTo(2);
        assertThat(resultado.nuevaVersion().cotizacionId()).isEqualTo(COTIZACION);

        // Se persisten dos filas: la rechazada y la nueva version.
        ArgumentCaptor<PruebaDiseno> guardadas = ArgumentCaptor.forClass(PruebaDiseno.class);
        verify(repositorio, times(2)).save(guardadas.capture());
        List<PruebaDiseno> capturadas = guardadas.getAllValues();
        assertThat(capturadas.get(0).getEstado()).isEqualTo(EstadoPruebaDiseno.RECHAZADA);
        assertThat(capturadas.get(1).getEstado()).isEqualTo(EstadoPruebaDiseno.PENDIENTE);
        assertThat(capturadas.get(1).getNumeroVersion()).isEqualTo(2);

        // Se auditan ambos eventos: el rechazo y la generacion por rechazo.
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(2)).registrar(ev.capture());
        assertThat(ev.getAllValues().get(0).accion()).isEqualTo("rechazar");
        assertThat(ev.getAllValues().get(1).accion()).isEqualTo("generar_por_rechazo");
    }

    @Test
    @DisplayName("rechazar computa el siguiente numero como max+1 (versionado monotono, Property 8)")
    void rechazarUsaMaximoVersion() {
        PruebaDiseno v3 = PruebaDiseno.siguienteVersion(COTIZACION, 3, "diseno");
        when(repositorio.findById(v3.getId())).thenReturn(Optional.of(v3));
        when(repositorio.save(any(PruebaDiseno.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repositorio.findMaxNumeroVersionByCotizacionId(COTIZACION)).thenReturn(3);

        ResultadoRechazoPruebaDiseno resultado = servicio.rechazar(v3.getId());

        assertThat(resultado.nuevaVersion().numeroVersion()).isEqualTo(4);
        verify(repositorio).findMaxNumeroVersionByCotizacionId(COTIZACION);
    }

    @Test
    @DisplayName("rechazar una prueba de otro tenant devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void rechazarInexistente() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.rechazar(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("prueba_diseno");
    }

    @Test
    @DisplayName("listar devuelve las Prueba_Diseno de la Cotizacion paginadas (Req 15.6)")
    void listar() {
        PruebaDiseno prueba = PruebaDiseno.generarInicial(COTIZACION, "diseno");
        Pageable pageable = PageRequest.of(0, 20);
        Page<PruebaDiseno> pagina = new PageImpl<>(List.of(prueba), pageable, 1);
        when(repositorio.findByCotizacionIdOrderByNumeroVersionDesc(eq(COTIZACION), any(Pageable.class)))
                .thenReturn(pagina);

        Page<PruebaDisenoDto> resultado = servicio.listar(COTIZACION, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).numeroVersion()).isEqualTo(1);
        verify(repositorio).findByCotizacionIdOrderByNumeroVersionDesc(eq(COTIZACION), any(Pageable.class));
    }
}
