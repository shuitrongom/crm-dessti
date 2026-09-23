package com.dessti.crm.platform.modulos.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link DependenciasModulosController}:
 * reproduce el contrato REST real del endpoint {@code GET
 * /plataforma/dependencias-modulos} sin BD y evalua realmente la expresion
 * {@code @PreAuthorize('plan','listar')} con un doble de {@code @autorizador}.
 * Replica el patron de {@link ModuloControllerTest} (mismo {@code @WebMvcTest},
 * mismo {@code @Import} de {@link MethodSecurityConfig} y
 * {@link ManejadorGlobalErrores}, y misma forma de simular {@code @autorizador}),
 * ajustando solo la ruta base y el JSON esperado.
 *
 * <p>Valida el Requisito 6.2: el catalogo de dependencias es consultable con el
 * mismo permiso que {@code /plataforma/modulos} ({@code plan:listar}) y devuelve
 * el mapa clave dependiente &rarr; requeridos (p. ej.
 * {@code {"inventario-avanzado":["operacion"]}}).</p>
 */
@WebMvcTest(controllers = DependenciasModulosController.class,
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
        DependenciasModulosControllerTest.ConfiguracionPrueba.class})
class DependenciasModulosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Autorizador autorizador;

    @Test
    void listar_devuelve200_paraSuperAdminConPermiso() throws Exception {
        // super_admin: posee el permiso plan:listar -> el autorizador devuelve true.
        when(autorizador.tiene("plan", "listar")).thenReturn(true);

        // El cuerpo se deriva de CatalogoDependenciasModulos: la unica dependencia
        // declarada hoy es inventario-avanzado -> [operacion].
        mockMvc.perform(get("/plataforma/dependencias-modulos").with(user("super_admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['inventario-avanzado']").isArray())
                .andExpect(jsonPath("$['inventario-avanzado'][0]").value("operacion"))
                .andExpect(jsonPath("$['inventario-avanzado'].length()").value(1));
    }

    @Test
    void listar_devuelve403_cuandoFaltaPermiso() throws Exception {
        // Sin el permiso plan:listar (autorizador devuelve false) -> 403.
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/plataforma/dependencias-modulos").with(user("rol_empresa")))
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
