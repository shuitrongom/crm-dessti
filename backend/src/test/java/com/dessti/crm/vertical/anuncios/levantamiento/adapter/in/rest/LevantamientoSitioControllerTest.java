package com.dessti.crm.vertical.anuncios.levantamiento.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoFotoDto;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoSitioDetalleDto;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoSitioDto;
import com.dessti.crm.vertical.anuncios.levantamiento.application.ServicioLevantamientos;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link LevantamientoSitioController} (tarea 21.1,
 * Req 16, 12). Reproduce el contrato REST real sin BD: {@link ServicioLevantamientos}
 * se simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble para
 * evaluar realmente las expresiones {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = LevantamientoSitioController.class,
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
        LevantamientoSitioControllerTest.ConfiguracionPrueba.class})
class LevantamientoSitioControllerTest {

    private static final UUID ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SITIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioLevantamientos servicioLevantamientos;

    @Autowired
    private Autorizador autorizador;

    private static LevantamientoSitioDto dto(String estado) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new LevantamientoSitioDto(ID, SITIO, null, null, "3x2", "muro", "220V",
                estado, "completado".equals(estado) ? "instalacion" : null,
                "completado".equals(estado) ? ahora : null, 0L, ahora, ahora);
    }

    private static LevantamientoSitioDetalleDto detalle(String estado, List<LevantamientoFotoDto> fotos) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new LevantamientoSitioDetalleDto(ID, SITIO, null, null, "3x2", "muro", "220V",
                estado, "completado".equals(estado) ? "instalacion" : null,
                "completado".equals(estado) ? ahora : null, 0L, ahora, ahora, fotos);
    }

    @BeforeEach
    void permitirGiroPorDefecto() {
        // El gating por Giro (tarea 8.4) se satisface por defecto para no romper el
        // camino feliz; cada test controla la autorizacion fina via {@code tiene(...)}.
        org.mockito.Mockito.lenient().when(autorizador.giroCorresponde(anyString())).thenReturn(true);
        // El gating por Plan (Req 25.4) tambien se satisface por defecto en el
        // camino feliz; los tests de 403 por modulo lo anulan explicitamente.
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    @Test
    void crear_devuelve201_enProceso() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "crear")).thenReturn(true);
        when(servicioLevantamientos.crear(any())).thenReturn(dto("en_proceso"));

        mockMvc.perform(post("/levantamientos")
                        .contentType("application/json")
                        .content("{\"mediciones\":\"3x2\",\"tipoSuperficie\":\"muro\","
                                + "\"condicionesElectricas\":\"220V\"}")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.estado").value("en_proceso"));
    }

    @Test
    void crear_propaga422_cuandoDatosInvalidos() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "crear")).thenReturn(true);
        when(servicioLevantamientos.crear(any()))
                .thenThrow(new ReglaNegocioException("dato obligatorio faltante"));

        mockMvc.perform(post("/levantamientos")
                        .contentType("application/json")
                        .content("{\"mediciones\":\"3x2\",\"tipoSuperficie\":\"muro\","
                                + "\"condicionesElectricas\":\"220V\"}")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crear_propaga404_cuandoVinculoNoExiste() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "crear")).thenReturn(true);
        when(servicioLevantamientos.crear(any()))
                .thenThrow(new RecursoNoEncontradoException("no existe la Cotizacion"));

        mockMvc.perform(post("/levantamientos")
                        .contentType("application/json")
                        .content("{\"mediciones\":\"3x2\",\"tipoSuperficie\":\"muro\","
                                + "\"condicionesElectricas\":\"220V\"}")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void consultar_devuelve200_conFotos() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "leer")).thenReturn(true);
        LevantamientoFotoDto foto = new LevantamientoFotoDto(
                UUID.randomUUID(), ID, "http://a/1.jpg", Instant.parse("2024-05-01T12:00:00Z"));
        when(servicioLevantamientos.consultarDetalle(eq(ID)))
                .thenReturn(detalle("en_proceso", List.of(foto)));

        mockMvc.perform(get("/levantamientos/{id}", ID).with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.fotos").isArray())
                .andExpect(jsonPath("$.fotos[0].referencia").value("http://a/1.jpg"));
    }

    @Test
    void consultar_devuelve404_cuandoNoAccesible() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "leer")).thenReturn(true);
        when(servicioLevantamientos.consultarDetalle(eq(ID)))
                .thenThrow(new RecursoNoEncontradoException("no accesible"));

        mockMvc.perform(get("/levantamientos/{id}", ID).with(user("instalacion")))
                .andExpect(status().isNotFound());
    }

    @Test
    void fotos_devuelve200_listaVacia() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "leer")).thenReturn(true);
        when(servicioLevantamientos.fotosDe(eq(ID))).thenReturn(List.of());

        mockMvc.perform(get("/levantamientos/{id}/fotos", ID).with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void fotos_devuelve200_conElementos() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "leer")).thenReturn(true);
        LevantamientoFotoDto foto = new LevantamientoFotoDto(
                UUID.randomUUID(), ID, "http://a/1.jpg", Instant.parse("2024-05-01T12:00:00Z"));
        when(servicioLevantamientos.fotosDe(eq(ID))).thenReturn(List.of(foto));

        mockMvc.perform(get("/levantamientos/{id}/fotos", ID).with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].referencia").value("http://a/1.jpg"));
    }

    @Test
    void fotos_devuelve404_cuandoNoAccesible() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "leer")).thenReturn(true);
        when(servicioLevantamientos.fotosDe(eq(ID)))
                .thenThrow(new RecursoNoEncontradoException("no accesible"));

        mockMvc.perform(get("/levantamientos/{id}/fotos", ID).with(user("instalacion")))
                .andExpect(status().isNotFound());
    }

    @Test
    void agregarFotos_devuelve201() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "crear")).thenReturn(true);
        when(servicioLevantamientos.agregarFotos(eq(ID), anyList()))
                .thenReturn(List.of(new LevantamientoFotoDto(
                        UUID.randomUUID(), ID, "http://a/1.jpg", Instant.parse("2024-05-01T12:00:00Z"))));

        mockMvc.perform(post("/levantamientos/{id}/fotos", ID)
                        .contentType("application/json")
                        .content("{\"referencias\":[\"http://a/1.jpg\"]}")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].referencia").value("http://a/1.jpg"));
    }

    @Test
    void completar_devuelve200_completado() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "cambiar_estado")).thenReturn(true);
        when(servicioLevantamientos.completar(eq(ID))).thenReturn(dto("completado"));

        mockMvc.perform(post("/levantamientos/{id}/completar", ID)
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("completado"))
                .andExpect(jsonPath("$.completadoPor").value("instalacion"));
    }

    @Test
    void completar_propaga409_cuandoYaCompletado() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "cambiar_estado")).thenReturn(true);
        when(servicioLevantamientos.completar(eq(ID)))
                .thenThrow(new TransicionInvalidaException("ya completado"));

        mockMvc.perform(post("/levantamientos/{id}/completar", ID)
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("levantamiento_sitio", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<LevantamientoSitioDto> pagina = new PageImpl<>(List.of(dto("en_proceso")), pageable, 1);
        when(servicioLevantamientos.listar(any(), any(), any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/levantamientos").with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/levantamientos")
                        .contentType("application/json")
                        .content("{\"mediciones\":\"3x2\",\"tipoSuperficie\":\"muro\","
                                + "\"condicionesElectricas\":\"220V\"}")
                        .with(user("sin_permiso")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
