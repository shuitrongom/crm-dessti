package com.dessti.crm.platform.empresas.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.empresas.EmpresaDto;
import com.dessti.crm.platform.empresas.EstadoEmpresa;
import com.dessti.crm.platform.empresas.ServicioEmpresas;
import com.dessti.crm.platform.empresas.ServicioSuscripciones;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link EmpresaController} centradas en la
 * reasignacion de Giro (Req 3): {@code PUT /empresas/{id}/giro}. Replican el
 * patron de {@code EmpresaControllerCuentaTest}: mockean {@link ServicioEmpresas}
 * y el bean {@code autorizador}, y ejercitan el contrato REST real sin BD.
 *
 * <p>Solo se verifica el contrato del endpoint (delegacion + mapeo de estados),
 * NO la logica del servicio {@code cambiarGiro} (ya cubierta en
 * {@code ServicioEmpresasTest}).</p>
 *
 * <p>Cubren: 200 con el DTO cuando el servicio responde (permiso concedido); 403
 * cuando falta el permiso {@code empresa:cambiar_estado}; 422 cuando el servicio
 * lanza {@link ReglaNegocioException} (Giro invalido/inactivo o datos del
 * vertical actual); y 404 cuando lanza {@link RecursoNoEncontradoException}
 * (Empresa inexistente).</p>
 */
@WebMvcTest(controllers = EmpresaController.class,
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
        EmpresaControllerTest.ConfiguracionPrueba.class})
class EmpresaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioEmpresas servicioEmpresas;

    @MockBean
    private ServicioSuscripciones servicioSuscripciones;

    @Autowired
    private Autorizador autorizador;

    private static EmpresaDto empresaConGiro(UUID empresaId, UUID giroId) {
        Instant ahora = Instant.now();
        return new EmpresaDto(
                empresaId,
                "Anuncios del Centro",
                "ACE120101AB1",
                giroId,
                EstadoEmpresa.ACTIVA,
                null, null, null, null, null, null,
                new EmpresaDto.DireccionDto(null, null, null, null, null),
                null, null, null, ahora, ahora,
                null);
    }

    @Test
    void cambiarGiro_conPermiso_devuelve200YElDto() throws Exception {
        UUID empresaId = UUID.randomUUID();
        UUID nuevoGiroId = UUID.randomUUID();
        when(autorizador.tiene("empresa", "cambiar_estado")).thenReturn(true);
        when(servicioEmpresas.cambiarGiro(eq(empresaId), eq(nuevoGiroId)))
                .thenReturn(empresaConGiro(empresaId, nuevoGiroId));

        mockMvc.perform(put("/empresas/{id}/giro", empresaId)
                        .with(user("super_admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giroId\":\"" + nuevoGiroId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(empresaId.toString()))
                .andExpect(jsonPath("$.giroId").value(nuevoGiroId.toString()));
    }

    @Test
    void cambiarGiro_sinPermiso_devuelve403() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(put("/empresas/{id}/giro", UUID.randomUUID())
                        .with(user("rol_empresa")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giroId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cambiarGiro_giroInvalidoODatosDelVertical_devuelve422() throws Exception {
        UUID empresaId = UUID.randomUUID();
        UUID nuevoGiroId = UUID.randomUUID();
        when(autorizador.tiene("empresa", "cambiar_estado")).thenReturn(true);
        doThrow(new ReglaNegocioException("El Giro destino no esta activo"))
                .when(servicioEmpresas).cambiarGiro(eq(empresaId), eq(nuevoGiroId));

        mockMvc.perform(put("/empresas/{id}/giro", empresaId)
                        .with(user("super_admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giroId\":\"" + nuevoGiroId + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cambiarGiro_empresaInexistente_devuelve404() throws Exception {
        UUID empresaId = UUID.randomUUID();
        UUID nuevoGiroId = UUID.randomUUID();
        when(autorizador.tiene("empresa", "cambiar_estado")).thenReturn(true);
        doThrow(new RecursoNoEncontradoException("Empresa inexistente"))
                .when(servicioEmpresas).cambiarGiro(eq(empresaId), eq(nuevoGiroId));

        mockMvc.perform(put("/empresas/{id}/giro", empresaId)
                        .with(user("super_admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giroId\":\"" + nuevoGiroId + "\"}"))
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
