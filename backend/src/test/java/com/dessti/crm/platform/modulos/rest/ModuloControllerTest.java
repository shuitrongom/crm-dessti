package com.dessti.crm.platform.modulos.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.modulos.CatalogoModulosService;
import com.dessti.crm.platform.modulos.ModuloCatalogoDto;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link ModuloController}: reproduce el contrato
 * REST real sin BD ({@link CatalogoModulosService} simulado) y evalua realmente
 * la expresion {@code @PreAuthorize('plan','listar')} con un doble de
 * {@code @autorizador}. Replica el patron de las pruebas de {@code GiroController}
 * y de los controladores de vertical.
 */
@WebMvcTest(controllers = ModuloController.class,
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
        ModuloControllerTest.ConfiguracionPrueba.class})
class ModuloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogoModulosService catalogoModulosService;

    @Autowired
    private Autorizador autorizador;

    @Test
    void listar_devuelve200_paraSuperAdminConPermiso() throws Exception {
        when(autorizador.tiene("plan", "listar")).thenReturn(true);
        UUID idComercial = UUID.fromString("11111111-1111-1111-1111-111111111111");
        // comercial: modulo de Nucleo CON precio de lista en la moneda principal (MXN).
        // produccion-industrial: clave solo de vertical (sin id ni precio).
        when(catalogoModulosService.listar()).thenReturn(List.of(
                new ModuloCatalogoDto("comercial", "Comercial (CRM)", null,
                        idComercial, new BigDecimal("1500.00"), "MXN"),
                new ModuloCatalogoDto("produccion-industrial", "Produccion industrial", "manufactura",
                        null, null, "MXN")));

        mockMvc.perform(get("/plataforma/modulos").with(user("super_admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].clave").value("comercial"))
                .andExpect(jsonPath("$[0].nombreVisible").value("Comercial (CRM)"))
                .andExpect(jsonPath("$[0].giro").value(nullValue()))
                .andExpect(jsonPath("$[0].catalogoModuloId").value(idComercial.toString()))
                .andExpect(jsonPath("$[0].precio").value(1500.00))
                .andExpect(jsonPath("$[0].monedaCodigo").value("MXN"))
                .andExpect(jsonPath("$[1].clave").value("produccion-industrial"))
                .andExpect(jsonPath("$[1].giro").value("manufactura"))
                .andExpect(jsonPath("$[1].catalogoModuloId").value(nullValue()))
                .andExpect(jsonPath("$[1].precio").value(nullValue()))
                .andExpect(jsonPath("$[1].monedaCodigo").value("MXN"));
    }

    @Test
    void listar_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/plataforma/modulos").with(user("rol_empresa")))
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
