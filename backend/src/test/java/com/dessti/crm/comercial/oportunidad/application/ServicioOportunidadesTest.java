package com.dessti.crm.comercial.oportunidad.application;

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

import com.dessti.crm.comercial.oportunidad.adapter.out.persistence.OportunidadRepository;
import com.dessti.crm.comercial.oportunidad.domain.EtapaOportunidad;
import com.dessti.crm.comercial.oportunidad.domain.Oportunidad;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioOportunidades} (Req 14). Usan dobles de
 * Mockito; no arrancan Spring ni base de datos. Cubren alta con verificacion del
 * Cliente (14.1), asignacion de responsable (14.2), cambio de etapa con
 * auditoria de la etapa anterior y la nueva (14.3, 14.9), rechazo de transicion
 * invalida (14.4 -> 409), guarda de conversion (14.6), listado con filtros
 * (14.7, 14.8) y acceso cruzado -> 404 + auditoria (23.3).
 */
class ServicioOportunidadesTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private OportunidadRepository repositorio;
    private ClienteExistentePort clienteExistente;
    private CanalVentaExistentePort canalVentaExistente;
    private AuditoriaPort auditoria;
    private CreacionCotizacionPort creacionCotizacion;
    private ServicioOportunidades servicio;

    @BeforeEach
    void setUp() {
        repositorio = mock(OportunidadRepository.class);
        clienteExistente = mock(ClienteExistentePort.class);
        canalVentaExistente = mock(CanalVentaExistentePort.class);
        auditoria = mock(AuditoriaPort.class);
        creacionCotizacion = mock(CreacionCotizacionPort.class);
        servicio = new ServicioOportunidades(repositorio, clienteExistente, canalVentaExistente,
                auditoria, Optional.of(creacionCotizacion));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CrearOportunidadCommand comandoValido() {
        return new CrearOportunidadCommand(CLIENTE, "Anuncio corporativo", new BigDecimal("15000.00"));
    }

    private Oportunidad enEtapa(EtapaOportunidad etapa) {
        Oportunidad o = Oportunidad.crear(CLIENTE, "Anuncio", new BigDecimal("15000.00"), "ventas");
        // Avanza por transiciones validas hasta la etapa pedida.
        switch (etapa) {
            case NUEVO -> { /* ya esta en nuevo */ }
            case CALIFICADO -> o.cambiarEtapa(EtapaOportunidad.CALIFICADO, "ventas");
            case PROPUESTA -> {
                o.cambiarEtapa(EtapaOportunidad.CALIFICADO, "ventas");
                o.cambiarEtapa(EtapaOportunidad.PROPUESTA, "ventas");
            }
            case NEGOCIACION -> {
                o.cambiarEtapa(EtapaOportunidad.CALIFICADO, "ventas");
                o.cambiarEtapa(EtapaOportunidad.PROPUESTA, "ventas");
                o.cambiarEtapa(EtapaOportunidad.NEGOCIACION, "ventas");
            }
            case GANADO -> {
                o.cambiarEtapa(EtapaOportunidad.CALIFICADO, "ventas");
                o.cambiarEtapa(EtapaOportunidad.PROPUESTA, "ventas");
                o.cambiarEtapa(EtapaOportunidad.NEGOCIACION, "ventas");
                o.cambiarEtapa(EtapaOportunidad.GANADO, "ventas");
            }
            case PERDIDO -> o.cambiarEtapa(EtapaOportunidad.PERDIDO, "ventas");
        }
        return o;
    }

    @Test
    @DisplayName("crearOportunidad verifica el Cliente, fija etapa 'nuevo' y audita (Req 14.1, 14.9)")
    void crearExitoso() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        when(repositorio.save(any(Oportunidad.class))).thenAnswer(inv -> inv.getArgument(0));

        OportunidadDto dto = servicio.crearOportunidad(comandoValido());

        assertThat(dto.etapa()).isEqualTo("nuevo");
        assertThat(dto.clienteId()).isEqualTo(CLIENTE);
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear");
        assertThat(ev.getValue().recurso()).isEqualTo("oportunidad");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("crearOportunidad con Cliente inexistente devuelve 404 y audita el acceso cruzado (Req 14.1, 23.3)")
    void crearClienteInexistente() {
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crearOportunidad(comandoValido()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cliente");
    }

    @Test
    @DisplayName("asignarResponsable persiste el responsable y audita (Req 14.2)")
    void asignarResponsable() {
        Oportunidad o = enEtapa(EtapaOportunidad.NUEVO);
        when(repositorio.findById(o.getId())).thenReturn(Optional.of(o));
        when(repositorio.save(any(Oportunidad.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID usuario = UUID.randomUUID();

        OportunidadDto dto = servicio.asignarResponsable(o.getId(), usuario);

        assertThat(dto.responsableUsuarioId()).isEqualTo(usuario);
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("asignar_responsable");
    }

    @Test
    @DisplayName("cambiarEtapa aplica la transicion y audita etapa anterior -> nueva (Req 14.3, 14.9)")
    void cambiarEtapaAudita() {
        Oportunidad o = enEtapa(EtapaOportunidad.NUEVO);
        when(repositorio.findById(o.getId())).thenReturn(Optional.of(o));
        when(repositorio.save(any(Oportunidad.class))).thenAnswer(inv -> inv.getArgument(0));

        OportunidadDto dto = servicio.cambiarEtapa(o.getId(), "calificado");

        assertThat(dto.etapa()).isEqualTo("calificado");
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("cambiar_etapa");
        assertThat(ev.getValue().valorAnterior()).isEqualTo("nuevo");
        assertThat(ev.getValue().valorNuevo()).isEqualTo("calificado");
    }

    @Test
    @DisplayName("cambiarEtapa invalida propaga 409 (Req 14.4)")
    void cambiarEtapaInvalida() {
        Oportunidad o = enEtapa(EtapaOportunidad.NUEVO);
        when(repositorio.findById(o.getId())).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> servicio.cambiarEtapa(o.getId(), "ganado"))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("convertirEnCotizacion delega en el puerto solo cuando la etapa es 'ganado' (Req 14.5)")
    void convertirGanado() {
        Oportunidad o = enEtapa(EtapaOportunidad.GANADO);
        UUID cotizacion = UUID.randomUUID();
        when(repositorio.findById(o.getId())).thenReturn(Optional.of(o));
        when(repositorio.save(any(Oportunidad.class))).thenAnswer(inv -> inv.getArgument(0));
        when(creacionCotizacion.crearDesdeOportunidad(eq(o.getId()), eq(CLIENTE), any()))
                .thenReturn(cotizacion);

        UUID resultado = servicio.convertirEnCotizacion(o.getId());

        assertThat(resultado).isEqualTo(cotizacion);
        assertThat(o.getCotizacionId()).isEqualTo(cotizacion);
        verify(creacionCotizacion).crearDesdeOportunidad(eq(o.getId()), eq(CLIENTE), any());
    }

    @Test
    @DisplayName("convertirEnCotizacion rechaza etapa distinta de 'ganado' y no crea Cotizacion (Req 14.6)")
    void convertirNoGanado() {
        Oportunidad o = enEtapa(EtapaOportunidad.PROPUESTA);
        when(repositorio.findById(o.getId())).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> servicio.convertirEnCotizacion(o.getId()))
                .isInstanceOf(ReglaNegocioException.class);

        verify(creacionCotizacion, never()).crearDesdeOportunidad(any(), any(), any());
    }

    @Test
    @DisplayName("consultarOportunidad de otro tenant devuelve 404 y audita el acceso cruzado (Req 23.3)")
    void consultarAccesoCruzado() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarOportunidad(ajeno))
                .isInstanceOf(RecursoNoEncontradoException.class);

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("oportunidad");
    }

    @Test
    @DisplayName("listarOportunidades traduce la etiqueta de etapa y proyecta a DTO (Req 14.7, 14.8)")
    void listarConFiltros() {
        Oportunidad o = enEtapa(EtapaOportunidad.NUEVO);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Oportunidad> pagina = new PageImpl<>(List.of(o), pageable, 1);
        when(repositorio.buscarConFiltros(eq(CLIENTE), eq(EtapaOportunidad.NUEVO), any(), any(), any(Pageable.class)))
                .thenReturn(pagina);

        Page<OportunidadDto> resultado = servicio.listarOportunidades(CLIENTE, "nuevo", null, null, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        verify(repositorio).buscarConFiltros(eq(CLIENTE), eq(EtapaOportunidad.NUEVO), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("listarOportunidades con etiqueta de etapa desconocida devuelve 422 (Req 14.8)")
    void listarEtapaDesconocida() {
        assertThatThrownBy(() -> servicio.listarOportunidades(null, "inexistente", null, null, PageRequest.of(0, 20)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("asignarCanalVenta verifica el canal, lo clasifica y audita anterior -> nuevo (Req 63.1, 63.3)")
    void asignarCanalVentaExitoso() {
        Oportunidad o = enEtapa(EtapaOportunidad.NUEVO);
        UUID canal = UUID.randomUUID();
        when(repositorio.findById(o.getId())).thenReturn(Optional.of(o));
        when(repositorio.save(any(Oportunidad.class))).thenAnswer(inv -> inv.getArgument(0));
        when(canalVentaExistente.existeCanalVentaActivo(canal)).thenReturn(true);

        OportunidadDto dto = servicio.asignarCanalVenta(o.getId(), canal);

        assertThat(dto.canalVentaId()).isEqualTo(canal);
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("asignar_canal");
        assertThat(ev.getValue().recurso()).isEqualTo("oportunidad");
        assertThat(ev.getValue().valorAnterior()).isNull();
        assertThat(ev.getValue().valorNuevo()).isEqualTo(canal.toString());
    }

    @Test
    @DisplayName("asignarCanalVenta con canal inexistente devuelve 404 y audita el acceso cruzado (Req 63.1, 23.3)")
    void asignarCanalVentaInexistente() {
        Oportunidad o = enEtapa(EtapaOportunidad.NUEVO);
        UUID canal = UUID.randomUUID();
        when(repositorio.findById(o.getId())).thenReturn(Optional.of(o));
        when(canalVentaExistente.existeCanalVentaActivo(canal)).thenReturn(false);

        assertThatThrownBy(() -> servicio.asignarCanalVenta(o.getId(), canal))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("canal_venta");
    }

    @Test
    @DisplayName("asignarCanalVenta con Oportunidad de otro tenant devuelve 404 y audita (Req 23.3)")
    void asignarCanalVentaOportunidadInexistente() {
        UUID ajeno = UUID.randomUUID();
        when(repositorio.findById(ajeno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarCanalVenta(ajeno, UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repositorio, never()).save(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("oportunidad");
    }

    @Test
    @DisplayName("asignarCanalVenta con canal nulo limpia la clasificacion sin verificar canal (Req 63.1)")
    void asignarCanalVentaNuloLimpia() {
        Oportunidad o = enEtapa(EtapaOportunidad.NUEVO);
        o.asignarCanalVenta(UUID.randomUUID(), "ventas");
        when(repositorio.findById(o.getId())).thenReturn(Optional.of(o));
        when(repositorio.save(any(Oportunidad.class))).thenAnswer(inv -> inv.getArgument(0));

        OportunidadDto dto = servicio.asignarCanalVenta(o.getId(), null);

        assertThat(dto.canalVentaId()).isNull();
        verify(canalVentaExistente, never()).existeCanalVentaActivo(any());
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("asignar_canal");
    }
}
