package com.dessti.crm.platform.empresas.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.empresas.ActualizarBrandingCommand;
import com.dessti.crm.platform.empresas.BrandingDto;
import com.dessti.crm.platform.empresas.ServicioBranding;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link BrandingController} centradas en el
 * color de marca (tarea 5.3; Req 6.2, 6.3, 6.4, 6.6, 1.8). Reproduce el contrato
 * REST real de {@code GET}/{@code PUT /empresa/branding} sin BD:
 * {@link ServicioBranding} se simula y el evaluador RBAC {@code @autorizador} se
 * sustituye por un doble para evaluar realmente las expresiones
 * {@code @PreAuthorize}. Sigue exactamente el patron de
 * {@code OrdenTrabajoInstalacionControllerTest} (slice {@code @WebMvcTest} con
 * {@link MethodSecurityConfig}, {@link ManejadorGlobalErrores} y el doble del
 * {@code @autorizador}).
 *
 * <p>El gating de estos endpoints es unicamente de <strong>autorizacion fina</strong>
 * ({@code @autorizador.tiene('branding', ...)}); el controlador NO usa
 * {@code moduloHabilitado} ni {@code giroCorresponde}, por lo que cada test
 * stubbea solo {@code tiene(...)} segun el permiso que ejerce.</p>
 *
 * <p>El {@code tenant_id} lo deriva el servicio del contexto autenticado (Req
 * 1.8, 6.6), nunca del cuerpo: por eso el JSON de la peticion nunca lo incluye
 * y el color viaja como {@code colorPrimario}.</p>
 */
@WebMvcTest(controllers = BrandingController.class,
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
        BrandingControllerColorTest.ConfiguracionPrueba.class})
class BrandingControllerColorTest {

    /** Color de marca valido en formato {@code #RRGGBB} (Req 6.4). */
    private static final String COLOR_VALIDO = "#1a2b3c";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioBranding servicioBranding;

    @Autowired
    private Autorizador autorizador;

    // ------------------------------------------------------------------
    // GET /empresa/branding: expone el color primario vigente (Req 6.2)
    // ------------------------------------------------------------------

    @Test
    void consultar_devuelve200_conColorPrimario() throws Exception {
        when(autorizador.tiene("branding", "leer")).thenReturn(true);
        when(servicioBranding.consultarBranding())
                .thenReturn(new BrandingDto("Marca", null, COLOR_VALIDO));

        mockMvc.perform(get("/empresa/branding").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreVisible").value("Marca"))
                .andExpect(jsonPath("$.colorPrimario").value(COLOR_VALIDO));
    }

    // ------------------------------------------------------------------
    // PUT /empresa/branding: persiste el color primario (Req 6.3, 6.4)
    // ------------------------------------------------------------------

    @Test
    void actualizar_devuelve200_conColorPrimarioValido() throws Exception {
        when(autorizador.tiene("branding", "actualizar")).thenReturn(true);
        when(servicioBranding.actualizarBranding(any(ActualizarBrandingCommand.class)))
                .thenReturn(new BrandingDto("Marca", null, COLOR_VALIDO));

        mockMvc.perform(put("/empresa/branding")
                        .contentType("application/json")
                        .content("{\"colorPrimario\":\"" + COLOR_VALIDO + "\"}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colorPrimario").value(COLOR_VALIDO));
    }

    @Test
    void actualizar_devuelve400_conColorPrimarioInvalido() throws Exception {
        // El permiso esta concedido: la peticion debe rechazarse por la violacion
        // de @Pattern (^#[0-9a-fA-F]{6}$) en ActualizarBrandingRequest.colorPrimario,
        // que se traduce a MethodArgumentNotValidException -> 400 (ManejadorGlobalErrores).
        when(autorizador.tiene("branding", "actualizar")).thenReturn(true);

        mockMvc.perform(put("/empresa/branding")
                        .contentType("application/json")
                        .content("{\"colorPrimario\":\"rojo\"}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Autorizacion fina: falta de permiso => 403 (Req 1.8, 6.6)
    // ------------------------------------------------------------------

    @Test
    void actualizar_devuelve403_cuandoFaltaPermisoActualizar() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(put("/empresa/branding")
                        .contentType("application/json")
                        .content("{\"colorPrimario\":\"" + COLOR_VALIDO + "\"}")
                        .with(user("x")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultar_devuelve403_cuandoFaltaPermisoLeer() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/empresa/branding").with(user("x")))
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
