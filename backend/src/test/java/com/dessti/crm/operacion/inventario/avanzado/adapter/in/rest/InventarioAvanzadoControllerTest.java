package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.operacion.inventario.avanzado.application.AlmacenDto;
import com.dessti.crm.operacion.inventario.avanzado.application.ServicioInventarioAvanzado;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link InventarioAvanzadoController} para la baja y
 * reactivacion logica de un Almacen (Req 60). {@link ServicioInventarioAvanzado} se
 * simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble para
 * evaluar realmente las expresiones {@code @PreAuthorize} (modulo inventario-avanzado +
 * almacen:actualizar).
 */
@WebMvcTest(controllers = InventarioAvanzadoController.class,
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
        InventarioAvanzadoControllerTest.ConfiguracionPrueba.class})
class InventarioAvanzadoControllerTest {

    private static final UUID ALMACEN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioInventarioAvanzado servicio;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        org.mockito.Mockito.lenient()
                .when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static AlmacenDto almacenDto(boolean activo) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new AlmacenDto(ALMACEN_ID, "Bodega Central", "bodega", activo, 0L, ahora, ahora);
    }

    @Test
    void desactivarAlmacen_devuelve200() throws Exception {
        when(autorizador.tiene("almacen", "actualizar")).thenReturn(true);
        when(servicio.desactivarAlmacen(eq(ALMACEN_ID))).thenReturn(almacenDto(false));

        mockMvc.perform(delete("/inventario-avanzado/almacenes/{id}", ALMACEN_ID)
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ALMACEN_ID.toString()))
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void desactivarAlmacen_devuelve404_cuandoNoAccesible() throws Exception {
        when(autorizador.tiene("almacen", "actualizar")).thenReturn(true);
        when(servicio.desactivarAlmacen(eq(ALMACEN_ID)))
                .thenThrow(new RecursoNoEncontradoException("No se encontro el Almacen solicitado."));

        mockMvc.perform(delete("/inventario-avanzado/almacenes/{id}", ALMACEN_ID)
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void activarAlmacen_devuelve200() throws Exception {
        when(autorizador.tiene("almacen", "actualizar")).thenReturn(true);
        when(servicio.activarAlmacen(eq(ALMACEN_ID))).thenReturn(almacenDto(true));

        mockMvc.perform(put("/inventario-avanzado/almacenes/{id}/activar", ALMACEN_ID)
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void desactivarAlmacen_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(delete("/inventario-avanzado/almacenes/{id}", ALMACEN_ID)
                        .with(user("sin_permiso")).with(csrf()))
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
