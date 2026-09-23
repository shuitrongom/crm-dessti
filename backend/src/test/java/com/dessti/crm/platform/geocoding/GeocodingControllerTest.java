package com.dessti.crm.platform.geocoding;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link GeocodingController} (revision R2):
 * reproducen el contrato REST de {@code /geocoding/direcciones} sin red real
 * ({@link ServicioGeocoding} simulado). Replican el patron de
 * {@code PerfilControllerTest} (utilidad autenticada sin permiso de modulo).
 *
 * <p>Cubren: 200 con la lista de sugerencias cuando el servicio la devuelve; 200
 * con lista vacia cuando {@code q} es corto (el servicio ya degrada); y 401 sin
 * autenticacion (convencion de la app: token ausente/invalido -> 401).</p>
 */
@WebMvcTest(controllers = GeocodingController.class,
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
        GeocodingControllerTest.ConfiguracionPrueba.class})
class GeocodingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioGeocoding servicioGeocoding;

    @Test
    void buscarDirecciones_autenticado_devuelve200ConLista() throws Exception {
        when(servicioGeocoding.buscar(eq("Toluca"))).thenReturn(List.of(
                new DireccionSugeridaDto(
                        "Avenida Juarez 100, Toluca, Estado de Mexico, Mexico",
                        "Avenida Juarez 100", "Toluca", "Estado de Mexico", "50000", "Mexico")));

        mockMvc.perform(get("/geocoding/direcciones").param("q", "Toluca").with(user("u")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiqueta")
                        .value("Avenida Juarez 100, Toluca, Estado de Mexico, Mexico"))
                .andExpect(jsonPath("$[0].calle").value("Avenida Juarez 100"))
                .andExpect(jsonPath("$[0].ciudad").value("Toluca"))
                .andExpect(jsonPath("$[0].estado").value("Estado de Mexico"))
                .andExpect(jsonPath("$[0].cp").value("50000"))
                .andExpect(jsonPath("$[0].pais").value("Mexico"));
    }

    @Test
    void buscarDirecciones_terminoCorto_devuelve200ConListaVacia() throws Exception {
        when(servicioGeocoding.buscar(eq("to"))).thenReturn(List.of());

        mockMvc.perform(get("/geocoding/direcciones").param("q", "to").with(user("u")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void buscarDirecciones_sinAutenticacion_devuelve401() throws Exception {
        mockMvc.perform(get("/geocoding/direcciones").param("q", "Toluca"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
