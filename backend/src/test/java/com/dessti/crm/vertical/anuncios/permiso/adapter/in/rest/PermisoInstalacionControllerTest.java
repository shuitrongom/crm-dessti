package com.dessti.crm.vertical.anuncios.permiso.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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

import com.dessti.crm.vertical.anuncios.permiso.application.PermisoInstalacionDto;
import com.dessti.crm.vertical.anuncios.permiso.application.ServicioPermisos;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link PermisoInstalacionController} (tarea 21.2,
 * Req 17, 12). Reproduce el contrato REST real sin BD: {@link ServicioPermisos} se
 * simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble para
 * evaluar realmente las expresiones {@code @PreAuthorize}. Mismo patron que
 * {@code LevantamientoSitioControllerTest}.
 */
@WebMvcTest(controllers = PermisoInstalacionController.class,
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
        PermisoInstalacionControllerTest.ConfiguracionPrueba.class})
class PermisoInstalacionControllerTest {

    private static final UUID ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SITIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioPermisos servicioPermisos;

    @Autowired
    private Autorizador autorizador;

    private static PermisoInstalacionDto dto(String estado) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        boolean decidido = !"solicitado".equals(estado);
        return new PermisoInstalacionDto(ID, SITIO, "municipal", LocalDate.of(2025, 5, 1),
                estado, decidido ? "instalacion" : null, decidido ? ahora : null,
                0L, ahora, ahora);
    }

    private static final String CUERPO_CREAR =
            "{\"tipo\":\"municipal\",\"fechaVencimiento\":\"2025-05-01\","
                    + "\"sitioId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"}";

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
    void crear_devuelve201_solicitado() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "crear")).thenReturn(true);
        when(servicioPermisos.crear(any())).thenReturn(dto("solicitado"));

        mockMvc.perform(post("/permisos-instalacion")
                        .contentType("application/json")
                        .content(CUERPO_CREAR)
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.estado").value("solicitado"))
                .andExpect(jsonPath("$.tipo").value("municipal"));
    }

    @Test
    void crear_propaga422_cuandoDatosInvalidos() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "crear")).thenReturn(true);
        when(servicioPermisos.crear(any()))
                .thenThrow(new ReglaNegocioException("tipo desconocido"));

        mockMvc.perform(post("/permisos-instalacion")
                        .contentType("application/json")
                        .content(CUERPO_CREAR)
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crear_rechaza400_cuandoFaltaCampoObligatorio() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "crear")).thenReturn(true);

        // Falta fechaVencimiento y sitioId -> Bean Validation (400).
        mockMvc.perform(post("/permisos-instalacion")
                        .contentType("application/json")
                        .content("{\"tipo\":\"municipal\"}")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultar_devuelve200() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "leer")).thenReturn(true);
        when(servicioPermisos.consultar(eq(ID))).thenReturn(dto("solicitado"));

        mockMvc.perform(get("/permisos-instalacion/{id}", ID).with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void consultar_propaga404_cuandoNoExiste() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "leer")).thenReturn(true);
        when(servicioPermisos.consultar(eq(ID)))
                .thenThrow(new RecursoNoEncontradoException("no existe"));

        mockMvc.perform(get("/permisos-instalacion/{id}", ID).with(user("instalacion")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cambiarEstado_aprobar_devuelve200() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "cambiar_estado")).thenReturn(true);
        when(servicioPermisos.aprobar(eq(ID))).thenReturn(dto("aprobado"));

        mockMvc.perform(put("/permisos-instalacion/{id}/estado", ID)
                        .param("accion", "aprobar")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("aprobado"))
                .andExpect(jsonPath("$.decididoPor").value("instalacion"));
    }

    @Test
    void cambiarEstado_rechazar_devuelve200() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "cambiar_estado")).thenReturn(true);
        when(servicioPermisos.rechazar(eq(ID))).thenReturn(dto("rechazado"));

        mockMvc.perform(put("/permisos-instalacion/{id}/estado", ID)
                        .param("accion", "rechazar")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("rechazado"));
    }

    @Test
    void cambiarEstado_propaga409_cuandoTransicionInvalida() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "cambiar_estado")).thenReturn(true);
        when(servicioPermisos.aprobar(eq(ID)))
                .thenThrow(new TransicionInvalidaException("ya en estado final"));

        mockMvc.perform(put("/permisos-instalacion/{id}/estado", ID)
                        .param("accion", "aprobar")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void cambiarEstado_rechaza422_cuandoAccionDesconocida() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "cambiar_estado")).thenReturn(true);

        mockMvc.perform(put("/permisos-instalacion/{id}/estado", ID)
                        .param("accion", "cancelar")
                        .with(user("instalacion")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("permiso_instalacion", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<PermisoInstalacionDto> pagina = new PageImpl<>(List.of(dto("solicitado")), pageable, 1);
        when(servicioPermisos.listar(any(), any(), any(), any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/permisos-instalacion").with(user("instalacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/permisos-instalacion")
                        .contentType("application/json")
                        .content(CUERPO_CREAR)
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
