package com.dessti.crm.operacion.produccion.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;
import com.dessti.crm.operacion.produccion.application.CrearOrdenDirectaCommand;
import com.dessti.crm.operacion.produccion.application.OrdenFabricacionDetalleDto;
import com.dessti.crm.operacion.produccion.application.OrdenFabricacionDto;
import com.dessti.crm.operacion.produccion.application.ServicioOrdenesFabricacion;

/**
 * Pruebas de rebanada (slice) del {@link OrdenFabricacionController} (tarea 19.1,
 * Req 7, 12). Reproduce el contrato REST real sin BD: {@link ServicioOrdenesFabricacion}
 * se simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble para
 * evaluar realmente las expresiones {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = OrdenFabricacionController.class,
        properties = {
                "DB_USER=test",
                "DB_PASSWORD=test",
                "JWT_SIGNING_KEY=clave-de-firma-jwt-solo-para-pruebas-0123456789",
                "CRM_ENC_KEY_ACTIVE=v1",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "crm.secretos.jwt-signing-key=clave-de-firma-jwt-solo-para-pruebas-0123456789"
        })
@Import({MethodSecurityConfig.class, ManejadorGlobalErrores.class,
        OrdenFabricacionControllerTest.ConfiguracionPrueba.class})
class OrdenFabricacionControllerTest {

    private static final UUID ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID COTIZACION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLIENTE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioOrdenesFabricacion servicioOrdenesFabricacion;

    @Autowired
    private Autorizador autorizador;

    private static OrdenFabricacionDto ordenDto(String estado) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new OrdenFabricacionDto(ID, COTIZACION, CLIENTE, estado, 0L, ahora, ahora);
    }

    // OF de origen generico (§A1, Req 1.1): sin Cotizacion asociada (cotizacionId nulo).
    private static OrdenFabricacionDto ordenDirectaDto(String estado) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new OrdenFabricacionDto(ID, null, CLIENTE, estado, 0L, ahora, ahora);
    }

    private static OrdenFabricacionDetalleDto detalleDto(String estado) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new OrdenFabricacionDetalleDto(
                ID, COTIZACION, CLIENTE, estado, 0L, ahora, ahora, List.of());
    }

    @BeforeEach
    void permitirGiroPorDefecto() {
        // El gating por Giro (tarea 8.4) se satisface por defecto para no romper el
        // camino feliz; cada test controla la autorizacion fina via @code tiene(...)}.
        org.mockito.Mockito.lenient().when(autorizador.giroCorresponde(anyString())).thenReturn(true);
        // El gating por Plan (Req 25.4) tambien se satisface por defecto en el
        // camino feliz; los tests de 403 por modulo lo anulan explicitamente.
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    @Test
    void generar_devuelve201_conOfPendiente() throws Exception {
        when(autorizador.tiene("orden_fabricacion", "crear")).thenReturn(true);
        when(servicioOrdenesFabricacion.generar(eq(COTIZACION))).thenReturn(ordenDto("pendiente"));

        mockMvc.perform(post("/ordenes-fabricacion")
                        .contentType("application/json")
                        .content("{\"cotizacionId\":\"" + COTIZACION + "\"}")
                        .with(user("produccion")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.cotizacionId").value(COTIZACION.toString()))
                .andExpect(jsonPath("$.estado").value("pendiente"));
    }

    @Test
    void generar_propaga422_cuandoCotizacionNoAprobada() throws Exception {
        when(autorizador.tiene("orden_fabricacion", "crear")).thenReturn(true);
        when(servicioOrdenesFabricacion.generar(eq(COTIZACION)))
                .thenThrow(new ReglaNegocioException("se requiere una Cotizacion aprobada"));

        mockMvc.perform(post("/ordenes-fabricacion")
                        .contentType("application/json")
                        .content("{\"cotizacionId\":\"" + COTIZACION + "\"}")
                        .with(user("produccion")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void generar_propaga409_cuandoOfYaExiste() throws Exception {
        when(autorizador.tiene("orden_fabricacion", "crear")).thenReturn(true);
        when(servicioOrdenesFabricacion.generar(eq(COTIZACION)))
                .thenThrow(new ConflictoUnicidadException(
                        "la Cotizacion ya tiene una Orden_Fabricacion asociada"));

        mockMvc.perform(post("/ordenes-fabricacion")
                        .contentType("application/json")
                        .content("{\"cotizacionId\":\"" + COTIZACION + "\"}")
                        .with(user("produccion")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void consultar_devuelve200() throws Exception {
        when(autorizador.tiene("orden_fabricacion", "leer")).thenReturn(true);
        when(servicioOrdenesFabricacion.consultar(eq(ID))).thenReturn(detalleDto("pendiente"));

        mockMvc.perform(get("/ordenes-fabricacion/{id}", ID).with(user("produccion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.partidas").isArray());
    }

    @Test
    void cambiarEstado_devuelve200_conNuevoEstado() throws Exception {
        when(autorizador.tiene("orden_fabricacion", "cambiar_estado")).thenReturn(true);
        when(servicioOrdenesFabricacion.cambiarEstado(eq(ID), eq("en_produccion")))
                .thenReturn(ordenDto("en_produccion"));

        mockMvc.perform(put("/ordenes-fabricacion/{id}/estado", ID)
                        .contentType("application/json")
                        .content("{\"estado\":\"en_produccion\"}")
                        .with(user("produccion")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("en_produccion"));
    }

    @Test
    void cambiarEstado_propaga409_cuandoTransicionInvalida() throws Exception {
        when(autorizador.tiene("orden_fabricacion", "cambiar_estado")).thenReturn(true);
        when(servicioOrdenesFabricacion.cambiarEstado(eq(ID), anyString()))
                .thenThrow(new TransicionInvalidaException("Transicion invalida."));

        mockMvc.perform(put("/ordenes-fabricacion/{id}/estado", ID)
                        .contentType("application/json")
                        .content("{\"estado\":\"terminada\"}")
                        .with(user("produccion")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("orden_fabricacion", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<OrdenFabricacionDto> pagina = new PageImpl<>(List.of(ordenDto("pendiente")), pageable, 1);
        when(servicioOrdenesFabricacion.listar(any(), any(), any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/ordenes-fabricacion").with(user("produccion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void generar_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/ordenes-fabricacion")
                        .contentType("application/json")
                        .content("{\"cotizacionId\":\"" + COTIZACION + "\"}")
                        .with(user("sin_permiso")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * Tarea 1.9 (§A2, Req 1.1, 1.3, 2.1): la OF de <strong>origen generico</strong>
     * es accesible para un Usuario de un giro <em>no-anuncios</em>. Tras relajar el
     * gating (tarea 1.3), el {@code @PreAuthorize} de OF ya NO exige
     * {@code giroCorresponde('operacion')}: basta con el modulo {@code operacion}
     * habilitado y el permiso atomico {@code orden_fabricacion:crear}. Aqui NO se
     * simula {@code giroCorresponde} para el camino feliz y ademas se verifica que
     * el gating <strong>no</strong> lo invoca (el acceso pasa solo con modulo +
     * permiso). El servicio devuelve una OF con {@code cotizacionId} nulo (Req 1.6).
     */
    @Test
    void crearDirecta_devuelve201_paraGiroNoAnuncios_sinExigirGiroCorresponde() throws Exception {
        // Solo modulo + permiso; NO se stubbea giroCorresponde (giro no-anuncios).
        when(autorizador.moduloHabilitado("operacion")).thenReturn(true);
        when(autorizador.tiene("orden_fabricacion", "crear")).thenReturn(true);
        when(servicioOrdenesFabricacion.crearDirecta(any(CrearOrdenDirectaCommand.class)))
                .thenReturn(ordenDirectaDto("pendiente"));

        mockMvc.perform(post("/ordenes-fabricacion/directa")
                        .contentType("application/json")
                        .content("{\"clienteId\":\"" + CLIENTE + "\",\"partidas\":[]}")
                        .with(user("generico")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.clienteId").value(CLIENTE.toString()))
                .andExpect(jsonPath("$.cotizacionId").doesNotExist())
                .andExpect(jsonPath("$.estado").value("pendiente"));

        // El gating de OF NO debe consultar el giro: el acceso pasa con modulo +
        // permiso, demostrando que la OF es de Nucleo (accesible a cualquier giro).
        verify(autorizador, never()).giroCorresponde(anyString());
    }

    /**
     * Tarea 1.9 (Req 2.1): sin el permiso {@code orden_fabricacion:crear} el endpoint
     * generico responde 403, aun con el modulo habilitado (el permiso atomico se
     * conserva tras relajar solo el giro).
     */
    @Test
    void crearDirecta_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/ordenes-fabricacion/directa")
                        .contentType("application/json")
                        .content("{\"clienteId\":\"" + CLIENTE + "\",\"partidas\":[]}")
                        .with(user("sin_permiso")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * Tarea 1.9 (no-regresion del flujo de anuncios, Req 14.4, 16.1): el endpoint de
     * genesis {@code POST /ordenes-fabricacion} ({@code {cotizacionId}}) sigue
     * existiendo y responde 201 con modulo + permiso. Las 3 precondiciones del flujo
     * de anuncios viven en el servicio y estan cubiertas por
     * {@code ServicioOrdenesFabricacionTest}/{@code PrecondicionesOrdenFabricacionPropertyTest};
     * aqui solo se verifica el contrato del endpoint (no se duplican).
     */
    @Test
    void generar_noRegresion_devuelve201_conModuloYPermiso() throws Exception {
        when(autorizador.moduloHabilitado("operacion")).thenReturn(true);
        when(autorizador.tiene("orden_fabricacion", "crear")).thenReturn(true);
        when(servicioOrdenesFabricacion.generar(eq(COTIZACION))).thenReturn(ordenDto("pendiente"));

        mockMvc.perform(post("/ordenes-fabricacion")
                        .contentType("application/json")
                        .content("{\"cotizacionId\":\"" + COTIZACION + "\"}")
                        .with(user("produccion")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.cotizacionId").value(COTIZACION.toString()))
                .andExpect(jsonPath("$.estado").value("pendiente"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
