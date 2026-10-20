package com.dessti.crm.compras.recepcion.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.compras.recepcion.application.PartidaRecepcionDto;
import com.dessti.crm.compras.recepcion.application.RecepcionMercanciaDto;
import com.dessti.crm.compras.recepcion.application.ServicioRecepciones;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link RecepcionMercanciaController} (Req 32):
 * códigos de estado y contrato REST (201/200/403/404/422), con el
 * {@link ServicioRecepciones} simulado y el {@code @autorizador} sustituido por un
 * doble para evaluar las expresiones @PreAuthorize.
 */
@WebMvcTest(controllers = RecepcionMercanciaController.class,
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
        RecepcionMercanciaControllerTest.ConfiguracionPrueba.class})
class RecepcionMercanciaControllerTest {

    private static final UUID REC_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OC_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PARTIDA_OC_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID MATERIAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioRecepciones servicio;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        org.mockito.Mockito.lenient()
                .when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static RecepcionMercanciaDto dto() {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new RecepcionMercanciaDto(REC_ID, OC_ID, ahora,
                List.of(new PartidaRecepcionDto(
                        UUID.fromString("99999999-9999-9999-9999-999999999999"),
                        PARTIDA_OC_ID, MATERIAL_ID, new BigDecimal("4"))),
                0L, ahora, ahora);
    }

    private static String cuerpoValido() {
        return "{\"ordenCompraId\":\"" + OC_ID + "\",\"partidas\":[{\"partidaOrdenCompraId\":\""
                + PARTIDA_OC_ID + "\",\"cantidadRecibida\":4}]}";
    }

    @Test
    void registrar_devuelve201() throws Exception {
        when(autorizador.tiene("recepcion_mercancia", "crear")).thenReturn(true);
        when(servicio.registrarRecepcion(any())).thenReturn(dto());

        mockMvc.perform(post("/compras/recepciones")
                        .with(user("almacen")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(REC_ID.toString()))
                .andExpect(jsonPath("$.ordenCompraId").value(OC_ID.toString()));
    }

    @Test
    void registrar_devuelve403_sinPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/compras/recepciones")
                        .with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrar_devuelve422_cuandoExcedeOrdenado() throws Exception {
        when(autorizador.tiene("recepcion_mercancia", "crear")).thenReturn(true);
        when(servicio.registrarRecepcion(any()))
                .thenThrow(new com.dessti.crm.platform.error.ReglaNegocioException(
                        "La cantidad recibida acumulada excede la cantidad ordenada."));

        mockMvc.perform(post("/compras/recepciones")
                        .with(user("almacen")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void registrar_devuelve404_cuandoOrdenNoAccesible() throws Exception {
        when(autorizador.tiene("recepcion_mercancia", "crear")).thenReturn(true);
        when(servicio.registrarRecepcion(any()))
                .thenThrow(new com.dessti.crm.platform.error.RecursoNoEncontradoException(
                        "No se encontro la Orden_Compra indicada para la recepcion."));

        mockMvc.perform(post("/compras/recepciones")
                        .with(user("almacen")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isNotFound());
    }

    @Test
    void consultar_devuelve200() throws Exception {
        when(autorizador.tiene("recepcion_mercancia", "leer")).thenReturn(true);
        when(servicio.consultar(eq(REC_ID))).thenReturn(dto());

        mockMvc.perform(get("/compras/recepciones/{id}", REC_ID).with(user("almacen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REC_ID.toString()));
    }

    @Test
    void consultar_devuelve404_cuandoNoAccesible() throws Exception {
        when(autorizador.tiene("recepcion_mercancia", "leer")).thenReturn(true);
        when(servicio.consultar(eq(REC_ID)))
                .thenThrow(new com.dessti.crm.platform.error.RecursoNoEncontradoException(
                        "No se encontro la Recepcion_Mercancia solicitada."));

        mockMvc.perform(get("/compras/recepciones/{id}", REC_ID).with(user("almacen")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_devuelve200_conLaPagina() throws Exception {
        when(autorizador.tiene("recepcion_mercancia", "listar")).thenReturn(true);
        when(servicio.listar(any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/compras/recepciones").with(user("almacen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(REC_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
