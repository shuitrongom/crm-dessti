package com.dessti.crm.platform.empresas.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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

import com.dessti.crm.platform.empresas.EstadoSuscripcion;
import com.dessti.crm.platform.empresas.ServicioSuscripciones;
import com.dessti.crm.platform.empresas.SuscripcionDto;
import com.dessti.crm.platform.empresas.TipoInstrumento;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link SuscripcionController} centradas en las
 * <strong>acciones de Contrato</strong> del rediseno
 * {@code plan-vs-suscripcion-contratacion}: {@code POST /{id}/activar-facturacion},
 * {@code POST /{id}/extender-prueba} y {@code POST /{id}/convertir-a-plan}.
 * Reproducen el contrato REST real sin BD ({@link ServicioSuscripciones}
 * simulado) y evaluan realmente las expresiones
 * {@code @PreAuthorize('suscripcion', ...)} con un doble de {@code @autorizador}.
 * Replican el patron de {@code PlanControllerTest} y {@code GiroControllerTest}.
 *
 * <p>Cubren (Req 4.3, 4.4, 8.4, 10): para cada accion, 200 con el permiso
 * atomico correspondiente ({@code activar-facturacion} &rarr;
 * {@code suscripcion:cambiar_estado}; {@code extender-prueba} &rarr;
 * {@code suscripcion:actualizar}; {@code convertir-a-plan} &rarr;
 * {@code suscripcion:crear}), 403 sin el permiso y 422 cuando el servicio lanza
 * {@link ReglaNegocioException} (regla de negocio incumplida).</p>
 */
@WebMvcTest(controllers = SuscripcionController.class,
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
        SuscripcionControllerContratoTest.ConfiguracionPrueba.class})
class SuscripcionControllerContratoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioSuscripciones servicioSuscripciones;

    @Autowired
    private Autorizador autorizador;

    private static SuscripcionDto suscripcionActiva(UUID id) {
        return new SuscripcionDto(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                TipoInstrumento.PLAN,
                null,
                EstadoSuscripcion.ACTIVA,
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                List.of("comercial"),
                "MXN",
                0L,
                Instant.now(),
                Instant.now());
    }

    // -----------------------------------------------------------------
    // POST /{id}/activar-facturacion  (perm suscripcion:cambiar_estado)
    // -----------------------------------------------------------------

    @Test
    void activarFacturacion_devuelve200_conPermisoCambiarEstado() throws Exception {
        UUID id = UUID.randomUUID();
        when(autorizador.tiene("suscripcion", "cambiar_estado")).thenReturn(true);
        when(servicioSuscripciones.activarFacturacion(eq(id), any(), any()))
                .thenReturn(suscripcionActiva(id));

        mockMvc.perform(post("/suscripciones/{id}/activar-facturacion", id)
                        .with(user("super_admin")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("activa"));
    }

    @Test
    void activarFacturacion_devuelve403_sinPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/suscripciones/{id}/activar-facturacion", UUID.randomUUID())
                        .with(user("rol_empresa")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void activarFacturacion_devuelve422_cuandoLaReglaDeNegocioLoImpide() throws Exception {
        when(autorizador.tiene("suscripcion", "cambiar_estado")).thenReturn(true);
        when(servicioSuscripciones.activarFacturacion(any(), any(), any()))
                .thenThrow(new ReglaNegocioException(
                        "Solo se puede activar la facturacion de un Contrato en periodo de prueba."));

        mockMvc.perform(post("/suscripciones/{id}/activar-facturacion", UUID.randomUUID())
                        .with(user("super_admin")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // -----------------------------------------------------------------
    // POST /{id}/extender-prueba  (perm suscripcion:actualizar)
    // -----------------------------------------------------------------

    @Test
    void extenderPrueba_devuelve200_conPermisoActualizar() throws Exception {
        UUID id = UUID.randomUUID();
        when(autorizador.tiene("suscripcion", "actualizar")).thenReturn(true);
        when(servicioSuscripciones.extenderPrueba(eq(id), any())).thenReturn(suscripcionActiva(id));

        mockMvc.perform(post("/suscripciones/{id}/extender-prueba", id)
                        .with(user("super_admin")).with(csrf())
                        .contentType("application/json")
                        .content("{\"nuevaVigenciaFin\":\"2030-12-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void extenderPrueba_devuelve403_sinPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/suscripciones/{id}/extender-prueba", UUID.randomUUID())
                        .with(user("rol_empresa")).with(csrf())
                        .contentType("application/json")
                        .content("{\"nuevaVigenciaFin\":\"2030-12-31\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void extenderPrueba_devuelve422_cuandoLaReglaDeNegocioLoImpide() throws Exception {
        when(autorizador.tiene("suscripcion", "actualizar")).thenReturn(true);
        when(servicioSuscripciones.extenderPrueba(any(), any()))
                .thenThrow(new ReglaNegocioException("Solo se puede extender una prueba en curso."));

        mockMvc.perform(post("/suscripciones/{id}/extender-prueba", UUID.randomUUID())
                        .with(user("super_admin")).with(csrf())
                        .contentType("application/json")
                        .content("{\"nuevaVigenciaFin\":\"2030-12-31\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // -----------------------------------------------------------------
    // POST /{id}/convertir-a-plan  (perm suscripcion:crear)
    // -----------------------------------------------------------------

    @Test
    void convertirAPlan_devuelve200_conPermisoCrear() throws Exception {
        UUID id = UUID.randomUUID();
        when(autorizador.tiene("suscripcion", "crear")).thenReturn(true);
        when(servicioSuscripciones.convertirAPlan(eq(id), any())).thenReturn(suscripcionActiva(id));

        mockMvc.perform(post("/suscripciones/{id}/convertir-a-plan", id)
                        .with(user("super_admin")).with(csrf())
                        .contentType("application/json")
                        .content("{\"planId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoInstrumento").value("plan"));
    }

    @Test
    void convertirAPlan_devuelve403_sinPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/suscripciones/{id}/convertir-a-plan", UUID.randomUUID())
                        .with(user("rol_empresa")).with(csrf())
                        .contentType("application/json")
                        .content("{\"planId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void convertirAPlan_devuelve422_cuandoLaReglaDeNegocioLoImpide() throws Exception {
        when(autorizador.tiene("suscripcion", "crear")).thenReturn(true);
        when(servicioSuscripciones.convertirAPlan(any(), any()))
                .thenThrow(new ReglaNegocioException("El Plan destino es obligatorio."));

        mockMvc.perform(post("/suscripciones/{id}/convertir-a-plan", UUID.randomUUID())
                        .with(user("super_admin")).with(csrf())
                        .contentType("application/json")
                        .content("{\"planId\":\"22222222-2222-2222-2222-222222222222\"}"))
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
