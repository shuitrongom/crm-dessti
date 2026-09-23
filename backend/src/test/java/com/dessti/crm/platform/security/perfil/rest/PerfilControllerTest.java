package com.dessti.crm.platform.security.perfil.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.perfil.PerfilDto;
import com.dessti.crm.platform.security.perfil.ServicioPerfil;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link PerfilController} (CHANGE 2): reproducen
 * el contrato REST real de {@code /auth/perfil} sin BD ({@link ServicioPerfil}
 * simulado). Replican el patron de {@code PlanControllerTest}/{@code GiroControllerTest}.
 *
 * <p>Cubren: GET perfil devuelve identificador/roles/tenant; PUT password con
 * contrasena actual correcta -> 204; contrasena actual incorrecta -> 422;
 * contrasena nueva demasiado corta -> 400 (Bean Validation); y 401 sin
 * autenticacion.</p>
 */
@WebMvcTest(controllers = PerfilController.class,
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
        PerfilControllerTest.ConfiguracionPrueba.class})
class PerfilControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioPerfil servicioPerfil;

    @Test
    void getPerfil_devuelveIdentificadorRolesYTenant() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(servicioPerfil.consultarPerfil())
                .thenReturn(new PerfilDto(id, "admin@empresa.com", List.of("admin_empresa"), tenant));

        mockMvc.perform(get("/auth/perfil").with(user("u")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificador").value("admin@empresa.com"))
                .andExpect(jsonPath("$.roles[0]").value("admin_empresa"))
                .andExpect(jsonPath("$.tenantId").value(tenant.toString()));
    }

    @Test
    void getPerfil_sinAutenticacion_devuelve401() throws Exception {
        mockMvc.perform(get("/auth/perfil"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putPassword_conActualCorrecta_devuelve204() throws Exception {
        mockMvc.perform(put("/auth/perfil/password")
                        .with(user("u")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"Actual123\",\"passwordNueva\":\"NuevaSegura12345\"}"))
                .andExpect(status().isNoContent());

        verify(servicioPerfil).cambiarPasswordPropia(eq("Actual123"), eq("NuevaSegura12345"));
    }

    @Test
    void putPassword_conActualIncorrecta_devuelve422() throws Exception {
        doThrow(new ReglaNegocioException("La contrasena actual no es correcta."))
                .when(servicioPerfil).cambiarPasswordPropia(eq("Mala"), eq("NuevaSegura12345"));

        mockMvc.perform(put("/auth/perfil/password")
                        .with(user("u")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"Mala\",\"passwordNueva\":\"NuevaSegura12345\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void putPassword_conNuevaDemasiadoCorta_devuelve400() throws Exception {
        mockMvc.perform(put("/auth/perfil/password")
                        .with(user("u")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"Actual123\",\"passwordNueva\":\"corta\"}"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
