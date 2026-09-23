package com.dessti.crm.social.adapter.in.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;
import com.dessti.crm.social.application.ConversacionDto;
import com.dessti.crm.social.application.ServicioBandeja;

/**
 * Pruebas de rebanada (slice) del endpoint de vinculacion del {@link BandejaController}
 * (tarea 4.3, Req 5.1, 5.2). Reproduce el contrato REST real sin BD:
 * {@link ServicioBandeja} se simula y el evaluador RBAC {@code @autorizador} se
 * sustituye por un doble para evaluar realmente las expresiones {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = BandejaController.class,
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
        BandejaControllerVincularTest.ConfiguracionPrueba.class})
class BandejaControllerVincularTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUENTA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final String CUERPO = """
            {"clienteId":"22222222-2222-2222-2222-222222222222"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServicioBandeja servicioBandeja;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static ConversacionDto conversacionVinculada() {
        Instant ahora = Instant.parse("2024-01-01T00:00:00Z");
        return new ConversacionDto(ID, CUENTA, "whatsapp", "5215500000000", CLIENTE, null,
                "abierta", null, null, 0L, ahora, ahora);
    }

    @Test
    void vincular_devuelve200_conClienteVinculado() throws Exception {
        when(autorizador.tiene("conversacion", "actualizar")).thenReturn(true);
        when(servicioBandeja.vincular(eq(ID), eq(CLIENTE))).thenReturn(conversacionVinculada());

        mockMvc.perform(put("/social/bandeja/{id}/vinculacion", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(CLIENTE.toString()));
    }

    @Test
    void vincular_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(put("/social/bandeja/{id}/vinculacion", ID).with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());
    }

    @Test
    void vincular_propaga404_cuandoClienteNoEncontrado() throws Exception {
        when(autorizador.tiene("conversacion", "actualizar")).thenReturn(true);
        when(servicioBandeja.vincular(eq(ID), eq(CLIENTE)))
                .thenThrow(new RecursoNoEncontradoException("No se encontro el Cliente indicado."));

        mockMvc.perform(put("/social/bandeja/{id}/vinculacion", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }

        @Bean
        ServicioBandeja servicioBandeja() {
            return org.mockito.Mockito.mock(ServicioBandeja.class);
        }
    }
}
