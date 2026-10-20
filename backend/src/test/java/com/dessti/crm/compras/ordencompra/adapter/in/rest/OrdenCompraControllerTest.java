package com.dessti.crm.compras.ordencompra.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.dessti.crm.compras.ordencompra.application.OrdenCompraDto;
import com.dessti.crm.compras.ordencompra.application.PartidaOrdenCompraDto;
import com.dessti.crm.compras.ordencompra.application.ServicioOrdenesCompra;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link OrdenCompraController} (Req 31): códigos de estado
 * y contrato REST (201/200/409/403), con el {@link ServicioOrdenesCompra} simulado y el
 * {@code @autorizador} sustituido por un doble para evaluar las expresiones @PreAuthorize.
 */
@WebMvcTest(controllers = OrdenCompraController.class,
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
        OrdenCompraControllerTest.ConfiguracionPrueba.class})
class OrdenCompraControllerTest {

    private static final UUID OC_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PROVEEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MATERIAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioOrdenesCompra servicio;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        org.mockito.Mockito.lenient()
                .when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static OrdenCompraDto dto(String estado) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new OrdenCompraDto(OC_ID, PROVEEDOR_ID, null, estado, new BigDecimal("300.00"),
                List.of(new PartidaOrdenCompraDto(
                        UUID.fromString("88888888-8888-8888-8888-888888888888"), MATERIAL_ID, 3,
                        new BigDecimal("100.00"), new BigDecimal("300.00"))),
                0L, ahora, ahora);
    }

    private static String cuerpoValido() {
        return "{\"proveedorId\":\"" + PROVEEDOR_ID + "\",\"partidas\":[{\"materialId\":\""
                + MATERIAL_ID + "\",\"cantidad\":3,\"precioUnitario\":100.00}]}";
    }

    @Test
    void crear_devuelve201() throws Exception {
        when(autorizador.tiene("orden_compra", "crear")).thenReturn(true);
        when(servicio.crearOrdenCompra(any())).thenReturn(dto("abierta"));

        mockMvc.perform(post("/compras/ordenes-compra")
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("abierta"))
                .andExpect(jsonPath("$.total").value(300.00));
    }

    @Test
    void crear_devuelve403_sinPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/compras/ordenes-compra")
                        .with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isForbidden());
    }

    @Test
    void crear_devuelve422_cuandoDatosInvalidos() throws Exception {
        when(autorizador.tiene("orden_compra", "crear")).thenReturn(true);
        when(servicio.crearOrdenCompra(any()))
                .thenThrow(new com.dessti.crm.platform.error.ReglaNegocioException(
                        "El precio unitario esta fuera del rango permitido."));

        mockMvc.perform(post("/compras/ordenes-compra")
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void consultar_devuelve404_cuandoNoAccesible() throws Exception {
        when(autorizador.tiene("orden_compra", "leer")).thenReturn(true);
        when(servicio.consultarOrdenCompra(eq(OC_ID)))
                .thenThrow(new com.dessti.crm.platform.error.RecursoNoEncontradoException(
                        "No se encontro la Orden_Compra solicitada."));

        mockMvc.perform(get("/compras/ordenes-compra/{id}", OC_ID).with(user("compras")))
                .andExpect(status().isNotFound());
    }

    @Test
    void consultar_devuelve200() throws Exception {
        when(autorizador.tiene("orden_compra", "leer")).thenReturn(true);
        when(servicio.consultarOrdenCompra(eq(OC_ID))).thenReturn(dto("abierta"));

        mockMvc.perform(get("/compras/ordenes-compra/{id}", OC_ID).with(user("compras")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OC_ID.toString()));
    }

    @Test
    void cambiarEstado_devuelve200() throws Exception {
        when(autorizador.tiene("orden_compra", "cambiar_estado")).thenReturn(true);
        when(servicio.cambiarEstado(eq(OC_ID), eq("cancelada"))).thenReturn(dto("cancelada"));

        mockMvc.perform(put("/compras/ordenes-compra/{id}/estado", OC_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"cancelada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cancelada"));
    }

    @Test
    void cambiarEstado_transicionInvalida_devuelve409() throws Exception {
        when(autorizador.tiene("orden_compra", "cambiar_estado")).thenReturn(true);
        when(servicio.cambiarEstado(eq(OC_ID), eq("cerrada")))
                .thenThrow(new TransicionInvalidaException("Transicion invalida."));

        mockMvc.perform(put("/compras/ordenes-compra/{id}/estado", OC_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"cerrada\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listar_devuelve200_conLaPagina() throws Exception {
        when(autorizador.tiene("orden_compra", "listar")).thenReturn(true);
        when(servicio.listarOrdenesCompra(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto("abierta")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/compras/ordenes-compra").with(user("compras")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].estado").value("abierta"))
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
