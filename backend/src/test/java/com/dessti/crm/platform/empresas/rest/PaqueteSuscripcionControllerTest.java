package com.dessti.crm.platform.empresas.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.empresas.PaqueteSuscripcionDto;
import com.dessti.crm.platform.empresas.ServicioPaquetesSuscripcion;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link PaqueteSuscripcionController}: reproducen
 * el contrato REST real de {@code /paquetes-suscripcion} sin BD
 * ({@link ServicioPaquetesSuscripcion} simulado) y evaluan realmente las
 * expresiones {@code @PreAuthorize('suscripcion', ...)} con un doble de
 * {@code @autorizador}. Replican el patron de {@code PlanControllerTest} y
 * {@code GiroControllerTest}.
 *
 * <p>Cubren (Req 3.1, 10.1, 10.5): 201 al crear con permiso {@code suscripcion:crear};
 * 403 sin permiso; 422 al eliminar un Paquete referenciado por un Contrato
 * (regla de negocio, {@code suscripcion:cambiar_estado}); 404 al consultar un
 * Paquete inexistente.</p>
 */
@WebMvcTest(controllers = PaqueteSuscripcionController.class,
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
        PaqueteSuscripcionControllerTest.ConfiguracionPrueba.class})
class PaqueteSuscripcionControllerTest {

    private static final String CUERPO_CREAR = """
            {
              "nombre": "Paquete Mensual",
              "maxUsuarios": 5,
              "duracionDias": 30,
              "admitePrueba": true,
              "duracionPruebaMeses": 1,
              "giroId": "11111111-1111-1111-1111-111111111111",
              "monedaCodigo": "MXN",
              "preciosModulos": { "comercial": 100.00 }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioPaquetesSuscripcion servicioPaquetesSuscripcion;

    @Autowired
    private Autorizador autorizador;

    private static PaqueteSuscripcionDto paqueteDe(String nombre) {
        return new PaqueteSuscripcionDto(
                UUID.randomUUID(),
                nombre,
                5,
                UUID.randomUUID(),
                "MXN",
                Map.of("comercial", new BigDecimal("100.00")),
                new BigDecimal("100.00"),
                List.of("comercial"),
                30,
                true,
                1,
                0L,
                Instant.now(),
                Instant.now());
    }

    @Test
    void crear_devuelve201_paraSuperAdminConPermiso() throws Exception {
        when(autorizador.tiene("suscripcion", "crear")).thenReturn(true);
        when(servicioPaquetesSuscripcion.crearPaquete(any())).thenReturn(paqueteDe("Paquete Mensual"));

        mockMvc.perform(post("/paquetes-suscripcion").with(user("super_admin")).with(csrf())
                        .contentType("application/json").content(CUERPO_CREAR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Paquete Mensual"))
                .andExpect(jsonPath("$.duracionDias").value(30))
                .andExpect(jsonPath("$.admitePrueba").value(true));
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/paquetes-suscripcion").with(user("rol_empresa")).with(csrf())
                        .contentType("application/json").content(CUERPO_CREAR))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminar_devuelve204_paraSuperAdminConPermiso() throws Exception {
        when(autorizador.tiene("suscripcion", "cambiar_estado")).thenReturn(true);
        // servicioPaquetesSuscripcion.eliminarPaquete es void: por defecto no hace nada (mock).

        mockMvc.perform(delete("/paquetes-suscripcion/{id}", UUID.randomUUID())
                        .with(user("super_admin")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminar_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(delete("/paquetes-suscripcion/{id}", UUID.randomUUID())
                        .with(user("rol_empresa")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminar_devuelve422_cuandoUnContratoReferenciaElPaquete() throws Exception {
        when(autorizador.tiene("suscripcion", "cambiar_estado")).thenReturn(true);
        doThrow(new ReglaNegocioException(
                "No se puede eliminar la Suscripcion 'Paquete Mensual': 2 Empresa(s) la tienen asignada. "
                        + "Primero cambia la contratacion de esas empresas."))
                .when(servicioPaquetesSuscripcion).eliminarPaquete(any());

        mockMvc.perform(delete("/paquetes-suscripcion/{id}", UUID.randomUUID())
                        .with(user("super_admin")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void eliminar_devuelve404_cuandoElPaqueteNoExiste() throws Exception {
        when(autorizador.tiene("suscripcion", "cambiar_estado")).thenReturn(true);
        doThrow(new RecursoNoEncontradoException("No se encontro la Suscripcion solicitada."))
                .when(servicioPaquetesSuscripcion).eliminarPaquete(any());

        mockMvc.perform(delete("/paquetes-suscripcion/{id}", UUID.randomUUID())
                        .with(user("super_admin")).with(csrf()))
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
