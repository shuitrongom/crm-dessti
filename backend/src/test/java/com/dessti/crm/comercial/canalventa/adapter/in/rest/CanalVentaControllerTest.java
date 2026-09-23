package com.dessti.crm.comercial.canalventa.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.comercial.canalventa.application.ActualizarCanalVentaCommand;
import com.dessti.crm.comercial.canalventa.application.CanalVentaDto;
import com.dessti.crm.comercial.canalventa.application.CrearCanalVentaCommand;
import com.dessti.crm.comercial.canalventa.application.ServicioCanalesVenta;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link CanalVentaController} (tarea 17.3, Req 63,
 * 12). Reproduce el contrato REST real sin BD: {@link ServicioCanalesVenta} se
 * simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble para
 * evaluar realmente las expresiones {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = CanalVentaController.class,
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
        CanalVentaControllerTest.ConfiguracionPrueba.class})
class CanalVentaControllerTest {

    private static final UUID ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioCanalesVenta servicioCanalesVenta;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        // El gating por Plan (Req 25.4) se satisface por defecto para no romper el
        // camino feliz; el test de 403 por modulo lo anula explicitamente.
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static CanalVentaDto canalDto(boolean activo) {
        Instant ahora = Instant.parse("2024-01-01T00:00:00Z");
        return new CanalVentaDto(ID, "Directo", "Venta directa", activo, 0L, ahora, ahora);
    }

    @Test
    void crear_devuelve201_conCanalDto() throws Exception {
        when(autorizador.tiene("canal_venta", "crear")).thenReturn(true);
        when(servicioCanalesVenta.crearCanal(any(CrearCanalVentaCommand.class)))
                .thenReturn(canalDto(true));

        mockMvc.perform(post("/canales-venta").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Directo","descripcion":"Venta directa"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.nombre").value("Directo"));
    }

    @Test
    void crear_devuelve409_cuandoNombreDuplicado() throws Exception {
        when(autorizador.tiene("canal_venta", "crear")).thenReturn(true);
        when(servicioCanalesVenta.crearCanal(any(CrearCanalVentaCommand.class)))
                .thenThrow(new ConflictoUnicidadException("Ya existe un canal activo."));

        mockMvc.perform(post("/canales-venta").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Directo"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void crear_devuelve400_cuandoNombreVacio() throws Exception {
        when(autorizador.tiene("canal_venta", "crear")).thenReturn(true);

        mockMvc.perform(post("/canales-venta").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultar_devuelve200() throws Exception {
        when(autorizador.tiene("canal_venta", "leer")).thenReturn(true);
        when(servicioCanalesVenta.consultarCanal(ID)).thenReturn(canalDto(true));

        mockMvc.perform(get("/canales-venta/{id}", ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Directo"));
    }

    @Test
    void consultar_propaga404() throws Exception {
        when(autorizador.tiene("canal_venta", "leer")).thenReturn(true);
        when(servicioCanalesVenta.consultarCanal(ID))
                .thenThrow(new RecursoNoEncontradoException("No se encontro."));

        mockMvc.perform(get("/canales-venta/{id}", ID).with(user("ventas")))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizar_devuelve200() throws Exception {
        when(autorizador.tiene("canal_venta", "actualizar")).thenReturn(true);
        when(servicioCanalesVenta.actualizarCanal(eq(ID), any(ActualizarCanalVentaCommand.class)))
                .thenReturn(canalDto(true));

        mockMvc.perform(put("/canales-venta/{id}", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Referido","descripcion":"Por recomendacion"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void eliminar_devuelve200_conCanalDesactivado() throws Exception {
        when(autorizador.tiene("canal_venta", "eliminar")).thenReturn(true);
        when(servicioCanalesVenta.desactivarCanal(ID)).thenReturn(canalDto(false));

        mockMvc.perform(delete("/canales-venta/{id}", ID).with(user("ventas")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("canal_venta", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<CanalVentaDto> pagina = new PageImpl<>(List.of(canalDto(true)), pageable, 1);
        when(servicioCanalesVenta.listarCanales(any(), any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/canales-venta").with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/canales-venta").with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Directo"}
                                """))
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
