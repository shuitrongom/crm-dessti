package com.dessti.crm.platform.empresas.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.empresas.ServicioPlanes;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link PlanController} centradas en el nuevo
 * endpoint {@code DELETE /planes/{id}}: reproducen el contrato REST real sin BD
 * ({@link ServicioPlanes} simulado) y evaluan realmente la expresion
 * {@code @PreAuthorize('plan','eliminar')} con un doble de {@code @autorizador}.
 * Replican el patron de {@code GiroControllerTest} y {@code ModuloControllerTest}.
 *
 * <p>Cubren: 204 al eliminar con permiso, 403 sin el permiso {@code plan:eliminar}
 * y 422 cuando la regla de negocio (Plan aun asignado a Empresas) impide el
 * borrado.</p>
 */
@WebMvcTest(controllers = PlanController.class,
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
        PlanControllerTest.ConfiguracionPrueba.class})
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioPlanes servicioPlanes;

    @Autowired
    private Autorizador autorizador;

    @Test
    void eliminar_devuelve204_paraSuperAdminConPermiso() throws Exception {
        when(autorizador.tiene("plan", "eliminar")).thenReturn(true);
        // servicioPlanes.eliminarPlan es void: por defecto no hace nada (mock).

        mockMvc.perform(delete("/planes/{id}", UUID.randomUUID())
                        .with(user("super_admin")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminar_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(delete("/planes/{id}", UUID.randomUUID())
                        .with(user("rol_empresa")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminar_devuelve422_cuandoElPlanTieneEmpresasAsignadas() throws Exception {
        when(autorizador.tiene("plan", "eliminar")).thenReturn(true);
        doThrow(new ReglaNegocioException(
                "No se puede eliminar el Plan 'Premium': 3 Empresa(s) lo tienen asignado. "
                        + "Primero cambia el plan de esas empresas."))
                .when(servicioPlanes).eliminarPlan(any());

        mockMvc.perform(delete("/planes/{id}", UUID.randomUUID())
                        .with(user("super_admin")).with(csrf()))
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
