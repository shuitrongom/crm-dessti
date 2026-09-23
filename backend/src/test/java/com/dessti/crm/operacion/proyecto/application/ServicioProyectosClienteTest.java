package com.dessti.crm.operacion.proyecto.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.ProyectoRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.SitioRepository;
import com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro;
import com.dessti.crm.operacion.proyecto.domain.Proyecto;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de <strong>ejemplo</strong> de la verificacion de existencia del
 * Cliente en {@link ServicioProyectos#crear(CrearProyectoCommand)} (Req 4.2, 4.3, 4.4,
 * 14.5). Complementan la {@code Property 6} (cubierta por
 * {@code VerificacionClientePropertyTest}) con ejemplos y bordes concretos, en el
 * mismo estilo que {@code ServicioOrdenesFabricacionTest}: JUnit + Mockito, sin
 * arrancar Spring ni base de datos.
 *
 * <p>Casos cubiertos:</p>
 * <ul>
 *   <li>Cliente inexistente/no accesible &rarr; 404 + auditoria de acceso cruzado con
 *       recurso {@code cliente}, sin persistir.</li>
 *   <li>{@code clienteId} nulo &rarr; 422 sin persistir (no se invoca {@code save}) ni
 *       se consulta la existencia del Cliente.</li>
 *   <li>Cliente existente &rarr; crea el Proyecto en {@code sin_sitios} y audita la
 *       creacion.</li>
 * </ul>
 */
class ServicioProyectosClienteTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private ProyectoRepository proyectoRepository;
    private SitioRepository sitioRepository;
    private ClienteExistentePort clienteExistente;
    private PerfilFasesGiroPort perfilFasesGiro;
    private AvanceSitioPort avanceProduccion;
    private AvanceSitioPort avanceSitioAnuncios;
    private AuditoriaPort auditoria;
    private ServicioProyectos servicio;

    @BeforeEach
    void setUp() {
        proyectoRepository = mock(ProyectoRepository.class);
        sitioRepository = mock(SitioRepository.class);
        clienteExistente = mock(ClienteExistentePort.class);
        perfilFasesGiro = mock(PerfilFasesGiroPort.class);
        avanceProduccion = mock(AvanceSitioPort.class);
        avanceSitioAnuncios = mock(AvanceSitioPort.class);
        auditoria = mock(AuditoriaPort.class);
        // Colaboradores no ejercitados por crear(): stubs neutrales/lenient.
        lenient().when(perfilFasesGiro.perfilDelTenant()).thenReturn(PerfilFasesGiro.GENERICO);
        lenient().when(proyectoRepository.save(any(Proyecto.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        servicio = new ServicioProyectos(
                proyectoRepository, sitioRepository, clienteExistente, perfilFasesGiro,
                avanceProduccion, avanceSitioAnuncios, auditoria);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("crear con Cliente inexistente devuelve 404 y audita el acceso cruzado con recurso 'cliente' (Req 4.2)")
    void crearClienteInexistente() {
        when(clienteExistente.existeEnTenant(CLIENTE)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crear(new CrearProyectoCommand(CLIENTE, "Proyecto de prueba")))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(proyectoRepository, never()).save(any(Proyecto.class));
        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("acceso_denegado");
        assertThat(ev.getValue().recurso()).isEqualTo("cliente");
        assertThat(ev.getValue().tenantId()).contains(TENANT);
    }

    @Test
    @DisplayName("crear con clienteId nulo devuelve 422 sin persistir ni verificar existencia (Req 4.3)")
    void crearClienteNulo() {
        assertThatThrownBy(() -> servicio.crear(new CrearProyectoCommand(null, "Proyecto de prueba")))
                .isInstanceOf(ReglaNegocioException.class);

        verify(proyectoRepository, never()).save(any(Proyecto.class));
        verify(clienteExistente, never()).existeEnTenant(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("crear con Cliente existente crea el Proyecto en 'sin_sitios' y audita (Req 4.4)")
    void crearClienteExistente() {
        when(clienteExistente.existeEnTenant(CLIENTE)).thenReturn(true);

        ProyectoDto dto = servicio.crear(new CrearProyectoCommand(CLIENTE, "Proyecto de prueba"));

        assertThat(dto).isNotNull();
        assertThat(dto.clienteId()).isEqualTo(CLIENTE);
        assertThat(dto.estadoConsolidado()).isEqualTo("sin_sitios");
        verify(proyectoRepository).save(any(Proyecto.class));

        ArgumentCaptor<EventoAuditoria> ev = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(ev.capture());
        assertThat(ev.getValue().accion()).isEqualTo("crear");
        assertThat(ev.getValue().recurso()).isEqualTo("proyecto");
    }
}
