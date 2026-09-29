package com.dessti.crm.operacion.proyecto.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaAvanceDto;
import com.dessti.crm.operacion.proyecto.application.evidencia.ServicioEvidenciasAvance;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link EvidenciaAvanceController}: subida
 * multipart, decision y su gobierno RBAC ({@code proyecto:actualizar} para subir,
 * {@code evidencia_avance:aprobar} para decidir), evaluando realmente las
 * expresiones {@code @PreAuthorize} con un doble de {@code @autorizador} y sin BD.
 */
@WebMvcTest(controllers = EvidenciaAvanceController.class,
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
        EvidenciaAvanceControllerTest.ConfiguracionPrueba.class})
class EvidenciaAvanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioEvidenciasAvance servicioEvidencias;

    @Autowired
    private Autorizador autorizador;

    private static EvidenciaAvanceDto pendiente(UUID id) {
        return new EvidenciaAvanceDto(
                id.toString(), UUID.randomUUID().toString(), "en_instalacion",
                "obra.jpg", "image/jpeg", 1234L, "pendiente", null,
                "operador", Instant.now(), null, null);
    }

    private static MockMultipartFile archivo() {
        return new MockMultipartFile("archivo", "obra.jpg", "image/jpeg", "bytes".getBytes());
    }

    // ---- POST subir (proyecto:actualizar) ----

    @Test
    void subir_devuelve201_conPermisoActualizar() throws Exception {
        UUID proyecto = UUID.randomUUID();
        UUID sitio = UUID.randomUUID();
        when(autorizador.moduloHabilitado("operacion")).thenReturn(true);
        when(autorizador.tiene("proyecto", "actualizar")).thenReturn(true);
        when(servicioEvidencias.subir(eq(proyecto), eq(sitio), any(), anyString(), anyString()))
                .thenReturn(pendiente(UUID.randomUUID()));

        mockMvc.perform(multipart("/proyectos/{id}/sitios/{sitioId}/evidencias", proyecto, sitio)
                        .file(archivo())
                        .with(user("operador")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("pendiente"));
    }

    @Test
    void subir_devuelve403_sinPermiso() throws Exception {
        when(autorizador.moduloHabilitado("operacion")).thenReturn(true);
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(multipart("/proyectos/{id}/sitios/{sitioId}/evidencias",
                        UUID.randomUUID(), UUID.randomUUID())
                        .file(archivo())
                        .with(user("rol_sin_permiso")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---- PUT decision (evidencia_avance:aprobar) ----

    @Test
    void decidir_aprobar_devuelve200_conPermiso() throws Exception {
        UUID evidencia = UUID.randomUUID();
        when(autorizador.moduloHabilitado("operacion")).thenReturn(true);
        when(autorizador.tiene("evidencia_avance", "aprobar")).thenReturn(true);
        when(servicioEvidencias.decidir(eq(evidencia), eq(true), any()))
                .thenReturn(pendiente(evidencia));

        mockMvc.perform(put("/evidencias-avance/{id}/decision", evidencia)
                        .param("accion", "aprobar")
                        .with(user("gerente")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void decidir_devuelve403_sinPermisoAprobar() throws Exception {
        when(autorizador.moduloHabilitado("operacion")).thenReturn(true);
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(put("/evidencias-avance/{id}/decision", UUID.randomUUID())
                        .param("accion", "aprobar")
                        .with(user("operador")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void decidir_accionInvalida_devuelve422() throws Exception {
        when(autorizador.moduloHabilitado("operacion")).thenReturn(true);
        when(autorizador.tiene("evidencia_avance", "aprobar")).thenReturn(true);

        mockMvc.perform(put("/evidencias-avance/{id}/decision", UUID.randomUUID())
                        .param("accion", "loquesea")
                        .with(user("gerente")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void decidir_rechazarSinMotivo_devuelve422() throws Exception {
        UUID evidencia = UUID.randomUUID();
        when(autorizador.moduloHabilitado("operacion")).thenReturn(true);
        when(autorizador.tiene("evidencia_avance", "aprobar")).thenReturn(true);
        when(servicioEvidencias.decidir(eq(evidencia), eq(false), any()))
                .thenThrow(new ReglaNegocioException("El motivo del rechazo es obligatorio."));

        mockMvc.perform(put("/evidencias-avance/{id}/decision", evidencia)
                        .param("accion", "rechazar")
                        .with(user("gerente")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
