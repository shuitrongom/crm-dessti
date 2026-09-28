package com.dessti.crm.portalcliente.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.comercial.cotizacion.adapter.out.persistence.CotizacionRepository;
import com.dessti.crm.comercial.cotizacion.application.CotizacionConsulta;
import com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort;
import com.dessti.crm.facturacion.factura.adapter.out.persistence.FacturaRepository;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioPortalCliente} (Req 45). Usan dobles de
 * Mockito; no arrancan Spring ni base de datos. Se centran en la guarda de
 * <strong>propiedad</strong> del Portal (Req 45.2, 45.3): aprobar/rechazar una
 * Prueba_Diseno que NO pertenece al Cliente del Portal responde 404 (no revela la
 * existencia del recurso ajeno) y jamas delega en el caso de uso de aprobacion.
 *
 * <p>Tras la extraccion del vertical de anuncios (Req 10.5), el Portal consume la
 * Prueba_Diseno por el puerto {@link ResumenPruebasDisenoPort} y la Cotizacion por
 * el puerto del Nucleo {@link CotizacionConsultaPort}; la guarda de propiedad se
 * verifica sobre esos puertos, no sobre las clases concretas del vertical.</p>
 */
class ServicioPortalClienteTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE_PORTAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTRO_CLIENTE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COTIZACION = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PRUEBA = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private ClientePortalActualPort clientePortalActual;
    private CotizacionRepository cotizacionRepository;
    private CotizacionConsultaPort cotizacionConsulta;
    private DecisionCotizacionPort decisionCotizacion;
    private ResumenPruebasDisenoPort resumenPruebasDiseno;
    private ResumenProyectosPort resumenProyectos;
    private ResumenTicketsPort resumenTickets;
    private RegistroQuejaPortalPort registroQuejaPortal;
    private PerfilClientePortalPort perfilClientePortal;
    private FacturaRepository facturaRepository;
    private AuditoriaPort auditoria;
    private ServicioPortalCliente servicio;

    @BeforeEach
    void setUp() {
        clientePortalActual = mock(ClientePortalActualPort.class);
        cotizacionRepository = mock(CotizacionRepository.class);
        cotizacionConsulta = mock(CotizacionConsultaPort.class);
        decisionCotizacion = mock(DecisionCotizacionPort.class);
        resumenPruebasDiseno = mock(ResumenPruebasDisenoPort.class);
        resumenProyectos = mock(ResumenProyectosPort.class);
        resumenTickets = mock(ResumenTicketsPort.class);
        registroQuejaPortal = mock(RegistroQuejaPortalPort.class);
        perfilClientePortal = mock(PerfilClientePortalPort.class);
        facturaRepository = mock(FacturaRepository.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioPortalCliente(
                clientePortalActual, cotizacionRepository, cotizacionConsulta, decisionCotizacion,
                resumenPruebasDiseno, resumenProyectos, resumenTickets, registroQuejaPortal,
                perfilClientePortal, facturaRepository, auditoria);
        TenantContext.set(TENANT);
        when(clientePortalActual.clienteIdActual()).thenReturn(CLIENTE_PORTAL);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("aprobarMiPruebaDiseno de una prueba de otro Cliente responde 404 y no delega (Req 45.2, 45.3)")
    void aprobarPruebaDeOtroClienteDa404() {
        when(resumenPruebasDiseno.cotizacionDePrueba(PRUEBA)).thenReturn(Optional.of(COTIZACION));
        when(cotizacionConsulta.buscar(COTIZACION)).thenReturn(Optional.of(
                new CotizacionConsulta(COTIZACION, "aprobada", OTRO_CLIENTE, BigDecimal.ZERO)));

        assertThatThrownBy(() -> servicio.aprobarMiPruebaDiseno(PRUEBA))
                .isInstanceOf(RecursoNoEncontradoException.class);

        // Nunca se delega en el caso de uso de aprobacion sobre un recurso ajeno.
        verify(resumenPruebasDiseno, never()).aprobar(any(UUID.class));
        // Se audita el intento de acceso cruzado (Req 45.5).
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("portal_cliente");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("rechazarMiPruebaDiseno de una prueba inexistente responde 404 y no delega (Req 45.3)")
    void rechazarPruebaInexistenteDa404() {
        when(resumenPruebasDiseno.cotizacionDePrueba(PRUEBA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.rechazarMiPruebaDiseno(PRUEBA))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(resumenPruebasDiseno, never()).rechazar(any(UUID.class));
    }

    @Test
    @DisplayName("aprobarMiCotizacion de una Cotizacion propia delega en el puerto y audita (Req 45.2)")
    void aprobarCotizacionPropiaDelega() {
        when(cotizacionConsulta.buscar(COTIZACION)).thenReturn(Optional.of(
                new CotizacionConsulta(COTIZACION, "enviada", CLIENTE_PORTAL, BigDecimal.TEN)));

        servicio.aprobarMiCotizacion(COTIZACION);

        verify(decisionCotizacion).aprobar(COTIZACION);
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("aprobar_cotizacion");
    }

    @Test
    @DisplayName("aprobarMiCotizacion de una Cotizacion de otro Cliente responde 404 y no delega (Req 45.3)")
    void aprobarCotizacionAjenaDa404() {
        when(cotizacionConsulta.buscar(COTIZACION)).thenReturn(Optional.of(
                new CotizacionConsulta(COTIZACION, "enviada", OTRO_CLIENTE, BigDecimal.TEN)));

        assertThatThrownBy(() -> servicio.aprobarMiCotizacion(COTIZACION))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(decisionCotizacion, never()).aprobar(any(UUID.class));
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
    }

    @Test
    @DisplayName("rechazarMiCotizacion de una Cotizacion inexistente responde 404 y no delega (Req 45.3)")
    void rechazarCotizacionInexistenteDa404() {
        when(cotizacionConsulta.buscar(COTIZACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.rechazarMiCotizacion(COTIZACION))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(decisionCotizacion, never()).rechazar(any(UUID.class));
    }

    @Test
    @DisplayName("registrarMiQueja registra con el Cliente del Portal y audita (Req 45.2, 70.1)")
    void registrarQuejaDelegaYAudita() {
        UUID quejaId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        when(registroQuejaPortal.registrarDesdePortal(CLIENTE_PORTAL, "No enciende el anuncio"))
                .thenReturn(new QuejaPortalResumen(
                        quejaId, "No enciende el anuncio", "registrada",
                        java.time.Instant.parse("2026-09-24T10:00:00Z")));

        QuejaPortalResumen resumen = servicio.registrarMiQueja("No enciende el anuncio");

        assertThat(resumen.id()).isEqualTo(quejaId);
        // Se registra SIEMPRE con el Cliente del Portal (nunca de la peticion).
        verify(registroQuejaPortal).registrarDesdePortal(CLIENTE_PORTAL, "No enciende el anuncio");
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("registrar_queja");
    }

    @Test
    @DisplayName("miPerfil resuelve el Cliente del Portal y devuelve su perfil (Req 45.1)")
    void miPerfilDelCliente() {
        when(perfilClientePortal.buscarPerfil(CLIENTE_PORTAL)).thenReturn(Optional.of(
                new PerfilClienteResumen(CLIENTE_PORTAL, "ACME S.A.", "ACME", "AAA010101AAA",
                        "contacto@acme.mx", "5555555555", "CDMX", "CDMX", "01000")));

        PerfilClienteResumen perfil = servicio.miPerfil();

        assertThat(perfil.clienteId()).isEqualTo(CLIENTE_PORTAL);
        assertThat(perfil.nombre()).isEqualTo("ACME S.A.");
        verify(perfilClientePortal).buscarPerfil(CLIENTE_PORTAL);
    }

    @Test
    @DisplayName("miPerfil responde 404 si el Cliente del Portal no es accesible (Req 45.3)")
    void miPerfilInaccesibleDa404() {
        when(perfilClientePortal.buscarPerfil(CLIENTE_PORTAL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.miPerfil())
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
