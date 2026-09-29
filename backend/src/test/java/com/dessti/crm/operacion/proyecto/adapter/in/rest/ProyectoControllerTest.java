package com.dessti.crm.operacion.proyecto.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.operacion.proyecto.application.ProyectoDto;
import com.dessti.crm.operacion.proyecto.application.ServicioProyectos;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link ProyectoController} para los endpoints de
 * edicion de Proyecto y de Sitio (Req 21.1, 21.2, 21.6). {@link ServicioProyectos} se
 * simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble para
 * evaluar realmente las expresiones {@code @PreAuthorize} (modulo operacion +
 * proyecto:actualizar).
 */
@WebMvcTest(controllers = ProyectoController.class,
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
        ProyectoControllerTest.ConfiguracionPrueba.class})
class ProyectoControllerTest {

    private static final UUID PROYECTO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SITIO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioProyectos servicioProyectos;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        org.mockito.Mockito.lenient()
                .when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static ProyectoDto proyectoDto(String nombre) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new ProyectoDto(PROYECTO_ID, CLIENTE_ID, nombre, null, List.of(),
                "en_preparacion", List.of(), 0L, ahora, ahora);
    }

    @Test
    void editarProyecto_devuelve200() throws Exception {
        when(autorizador.tiene("proyecto", "actualizar")).thenReturn(true);
        when(servicioProyectos.editarProyecto(eq(PROYECTO_ID), eq("Proyecto renombrado")))
                .thenReturn(proyectoDto("Proyecto renombrado"));

        mockMvc.perform(put("/proyectos/{id}", PROYECTO_ID)
                        .contentType("application/json")
                        .content("{\"nombre\":\"Proyecto renombrado\"}")
                        .with(user("gerente")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROYECTO_ID.toString()))
                .andExpect(jsonPath("$.nombre").value("Proyecto renombrado"));
    }

    @Test
    void editarProyecto_devuelve404_cuandoNoAccesible() throws Exception {
        when(autorizador.tiene("proyecto", "actualizar")).thenReturn(true);
        when(servicioProyectos.editarProyecto(eq(PROYECTO_ID), anyString()))
                .thenThrow(new RecursoNoEncontradoException("No se encontro el Proyecto solicitado."));

        mockMvc.perform(put("/proyectos/{id}", PROYECTO_ID)
                        .contentType("application/json")
                        .content("{\"nombre\":\"X\"}")
                        .with(user("gerente")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void editarProyecto_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(put("/proyectos/{id}", PROYECTO_ID)
                        .contentType("application/json")
                        .content("{\"nombre\":\"X\"}")
                        .with(user("sin_permiso")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarSitio_devuelve200() throws Exception {
        when(autorizador.tiene("proyecto", "actualizar")).thenReturn(true);
        when(servicioProyectos.editarSitio(eq(PROYECTO_ID), eq(SITIO_ID),
                eq("Sucursal Centro"), any()))
                .thenReturn(proyectoDto("Proyecto A"));

        mockMvc.perform(put("/proyectos/{id}/sitios/{sitioId}", PROYECTO_ID, SITIO_ID)
                        .contentType("application/json")
                        .content("{\"nombre\":\"Sucursal Centro\",\"direccion\":\"Av. Juarez 100\"}")
                        .with(user("gerente")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROYECTO_ID.toString()));
    }

    @Test
    void editarSitio_devuelve404_cuandoSitioNoPertenece() throws Exception {
        when(autorizador.tiene("proyecto", "actualizar")).thenReturn(true);
        when(servicioProyectos.editarSitio(eq(PROYECTO_ID), eq(SITIO_ID), anyString(), any()))
                .thenThrow(new RecursoNoEncontradoException(
                        "No se encontro el Sitio solicitado en el Proyecto."));

        mockMvc.perform(put("/proyectos/{id}/sitios/{sitioId}", PROYECTO_ID, SITIO_ID)
                        .contentType("application/json")
                        .content("{\"nombre\":\"X\"}")
                        .with(user("gerente")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
