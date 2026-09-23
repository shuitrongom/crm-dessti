package com.dessti.crm.platform.giros.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.giros.GiroDto;
import com.dessti.crm.platform.giros.application.ServicioGiros;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link GiroController}: reproducen el contrato
 * REST real sin BD ({@link ServicioGiros} simulado) y evaluan realmente las
 * expresiones {@code @PreAuthorize('giro', ...)} con un doble de
 * {@code @autorizador}. Replican el patron de {@code ModuloControllerTest}.
 *
 * <p>El foco de estas pruebas es que el JSON de salida exponga los campos de
 * <strong>completitud</strong> del Giro ({@code tieneReglasNegocio},
 * {@code modulosEspecificos}) que enriquece {@link ServicioGiros}, tanto en el
 * listado como en el alta, ademas de la denegacion 403 por falta de permiso.</p>
 */
@WebMvcTest(controllers = GiroController.class,
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
        GiroControllerTest.ConfiguracionPrueba.class})
class GiroControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioGiros servicioGiros;

    @Autowired
    private Autorizador autorizador;

    @Test
    void listar_exponeCompletitudDeCadaGiro_paraSuperAdminConPermiso() throws Exception {
        when(autorizador.tiene("giro", "listar")).thenReturn(true);
        GiroDto completo = new GiroDto(UUID.randomUUID(), "anuncios-luminosos",
                "Anuncios Luminosos", null, true, 0L, true, 2);
        GiroDto base = new GiroDto(UUID.randomUUID(), "manufactura",
                "Manufactura", null, true, 0L, false, 0);
        Page<GiroDto> pagina = new PageImpl<>(List.of(completo, base), PageRequest.of(0, 20), 2);
        when(servicioGiros.listar(any(), any())).thenReturn(pagina);

        mockMvc.perform(get("/plataforma/giros").with(user("super_admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clave").value("anuncios-luminosos"))
                .andExpect(jsonPath("$.content[0].tieneReglasNegocio").value(true))
                .andExpect(jsonPath("$.content[0].modulosEspecificos").value(2))
                .andExpect(jsonPath("$.content[1].clave").value("manufactura"))
                .andExpect(jsonPath("$.content[1].tieneReglasNegocio").value(false))
                .andExpect(jsonPath("$.content[1].modulosEspecificos").value(0));
    }

    @Test
    void crear_devuelve201ConGiroBase_exponiendoCompletitud() throws Exception {
        when(autorizador.tiene("giro", "crear")).thenReturn(true);
        GiroDto creado = new GiroDto(UUID.randomUUID(), "logistica",
                "Logistica", "Giro nuevo", true, 0L, false, 0);
        when(servicioGiros.crear(anyString(), anyString(), any())).thenReturn(creado);

        mockMvc.perform(post("/plataforma/giros").with(user("super_admin")).with(csrf())
                        .contentType("application/json")
                        .content("{\"clave\":\"logistica\",\"nombreVisible\":\"Logistica\",\"descripcion\":\"Giro nuevo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clave").value("logistica"))
                .andExpect(jsonPath("$.tieneReglasNegocio").value(false))
                .andExpect(jsonPath("$.modulosEspecificos").value(0));
    }

    @Test
    void listar_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/plataforma/giros").with(user("rol_empresa")))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminar_devuelve204_paraSuperAdminConPermiso() throws Exception {
        when(autorizador.tiene("giro", "eliminar")).thenReturn(true);
        // servicioGiros.eliminar es void: por defecto no hace nada (mock).

        mockMvc.perform(delete("/plataforma/giros/{id}", UUID.randomUUID())
                        .with(user("super_admin")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminar_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(delete("/plataforma/giros/{id}", UUID.randomUUID())
                        .with(user("rol_empresa")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminar_devuelve422_cuandoLaReglaDeNegocioLoImpide() throws Exception {
        when(autorizador.tiene("giro", "eliminar")).thenReturn(true);
        doThrow(new ReglaNegocioException(
                "No se puede eliminar el Giro 'X': ya tiene reglas de negocio programadas y es definitivo."))
                .when(servicioGiros).eliminar(any());

        mockMvc.perform(delete("/plataforma/giros/{id}", UUID.randomUUID())
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
