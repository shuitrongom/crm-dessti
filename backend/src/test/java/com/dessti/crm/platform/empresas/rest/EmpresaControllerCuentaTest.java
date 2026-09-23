package com.dessti.crm.platform.empresas.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.dessti.crm.platform.empresas.ResetPasswordAdminDto;
import com.dessti.crm.platform.empresas.ServicioEmpresas;
import com.dessti.crm.platform.empresas.ServicioSuscripciones;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link EmpresaController} centradas en la
 * gestion de cuenta: el restablecimiento de la contrasena del
 * {@code admin_empresa} (CHANGE 3) y la obligatoriedad del correo de contacto en
 * el alta (CHANGE 4). Replican el patron de {@code PlanControllerTest}.
 *
 * <p>Cubren: reset con contrasena explicita -> 200 + passwordTemporal null; reset
 * sin contrasena -> 200 + passwordTemporal no vacio; 403 sin permiso; 404 cuando
 * no hay admin; 422 con contrasena explicita invalida; y 400 en el alta cuando
 * falta {@code emailContacto} (ahora obligatorio).</p>
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
        EmpresaControllerCuentaTest.ConfiguracionPrueba.class})
class EmpresaControllerCuentaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioEmpresas servicioEmpresas;

    @MockBean
    private ServicioSuscripciones servicioSuscripciones;

    @Autowired
    private Autorizador autorizador;

    @Test
    void resetPassword_conPasswordExplicita_devuelve200YPasswordTemporalNull() throws Exception {
        UUID empresaId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(autorizador.tiene("empresa", "cambiar_estado")).thenReturn(true);
        when(servicioEmpresas.restablecerPasswordAdmin(eq(empresaId), eq("NuevaSegura12345"), isNull()))
                .thenReturn(new ResetPasswordAdminDto(usuarioId, "admin@empresa.com", null));

        mockMvc.perform(post("/empresas/{id}/admin/reset-password", empresaId)
                        .with(user("super_admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NuevaSegura12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.identificador").value("admin@empresa.com"))
                .andExpect(jsonPath("$.passwordTemporal").doesNotExist());
    }

    @Test
    void resetPassword_sinCuerpo_generaTemporal_devuelve200YPasswordTemporalNoVacio() throws Exception {
        UUID empresaId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(autorizador.tiene("empresa", "cambiar_estado")).thenReturn(true);
        when(servicioEmpresas.restablecerPasswordAdmin(eq(empresaId), isNull(), isNull()))
                .thenReturn(new ResetPasswordAdminDto(usuarioId, "admin@empresa.com", "Temp0ral-XyZ"));

        mockMvc.perform(post("/empresas/{id}/admin/reset-password", empresaId)
                        .with(user("super_admin")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordTemporal").value("Temp0ral-XyZ"));
    }

    @Test
    void resetPassword_sinPermiso_devuelve403() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/empresas/{id}/admin/reset-password", UUID.randomUUID())
                        .with(user("rol_empresa")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetPassword_sinAdminEmpresa_devuelve404() throws Exception {
        UUID empresaId = UUID.randomUUID();
        when(autorizador.tiene("empresa", "cambiar_estado")).thenReturn(true);
        doThrow(new RecursoNoEncontradoException("sin admin"))
                .when(servicioEmpresas).restablecerPasswordAdmin(eq(empresaId), isNull(), isNull());

        mockMvc.perform(post("/empresas/{id}/admin/reset-password", empresaId)
                        .with(user("super_admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetPassword_conPasswordExplicitaInvalida_devuelve422() throws Exception {
        UUID empresaId = UUID.randomUUID();
        when(autorizador.tiene("empresa", "cambiar_estado")).thenReturn(true);
        doThrow(new ReglaNegocioException("contrasena invalida"))
                .when(servicioEmpresas).restablecerPasswordAdmin(eq(empresaId), anyString(), isNull());

        // La cota 8..255 la valida el servicio para este caso (mock lanza 422).
        mockMvc.perform(post("/empresas/{id}/admin/reset-password", empresaId)
                        .with(user("super_admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"aceptable123\"}"))
                .andExpect(status().isUnprocessableEntity());

        verify(servicioEmpresas).restablecerPasswordAdmin(eq(empresaId), anyString(), isNull());
    }

    @Test
    void crearEmpresa_sinEmailContacto_devuelve400() throws Exception {
        when(autorizador.tiene("empresa", "crear")).thenReturn(true);

        // Cuerpo con todos los datos obligatorios EXCEPTO emailContacto (ahora
        // obligatorio, CHANGE 4): la validacion de Bean Validation responde 400
        // antes de invocar el servicio.
        String body = "{"
                + "\"nombre\":\"Anuncios del Norte\","
                + "\"rfc\":\"ANO120101AB1\","
                + "\"giroId\":\"" + UUID.randomUUID() + "\","
                + "\"planId\":\"" + UUID.randomUUID() + "\","
                + "\"adminIdentificador\":\"admin@anuncios.com\""
                + "}";

        mockMvc.perform(post("/empresas")
                        .with(user("super_admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(servicioEmpresas, org.mockito.Mockito.never()).crearEmpresa(any());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
