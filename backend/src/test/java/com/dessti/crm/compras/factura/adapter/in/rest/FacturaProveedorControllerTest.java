package com.dessti.crm.compras.factura.adapter.in.rest;

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

import com.dessti.crm.compras.factura.application.FacturaProveedorDto;
import com.dessti.crm.compras.factura.application.ServicioFacturasProveedor;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link FacturaProveedorController} (Req 33):
 * códigos de estado y contrato REST (201/200/403/404/422), con el
 * {@link ServicioFacturasProveedor} simulado y el {@code @autorizador} sustituido
 * por un doble para evaluar las expresiones @PreAuthorize.
 */
@WebMvcTest(controllers = FacturaProveedorController.class,
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
        FacturaProveedorControllerTest.ConfiguracionPrueba.class})
class FacturaProveedorControllerTest {

    private static final UUID FAC_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OC_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PROVEEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioFacturasProveedor servicio;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        org.mockito.Mockito.lenient()
                .when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static FacturaProveedorDto dto(String estado) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new FacturaProveedorDto(FAC_ID, OC_ID, PROVEEDOR_ID, "F-1001",
                new BigDecimal("1000.00"), estado, 0L, ahora, ahora);
    }

    private static String cuerpoValido() {
        return "{\"ordenCompraId\":\"" + OC_ID + "\",\"folioProveedor\":\"F-1001\",\"monto\":1000.00}";
    }

    @Test
    void registrar_devuelve201() throws Exception {
        when(autorizador.tiene("factura_proveedor", "crear")).thenReturn(true);
        when(servicio.registrarFactura(any())).thenReturn(dto("registrada"));

        mockMvc.perform(post("/compras/facturas-proveedor")
                        .with(user("almacen")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("registrada"))
                .andExpect(jsonPath("$.proveedorId").value(PROVEEDOR_ID.toString()));
    }

    @Test
    void registrar_devuelve403_sinPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/compras/facturas-proveedor")
                        .with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrar_devuelve404_cuandoOrdenNoExiste() throws Exception {
        when(autorizador.tiene("factura_proveedor", "crear")).thenReturn(true);
        when(servicio.registrarFactura(any()))
                .thenThrow(new com.dessti.crm.platform.error.RecursoNoEncontradoException(
                        "No se encontro la Orden_Compra indicada para la Factura_Proveedor."));

        mockMvc.perform(post("/compras/facturas-proveedor")
                        .with(user("almacen")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isNotFound());
    }

    @Test
    void conciliar_devuelve200() throws Exception {
        when(autorizador.tiene("factura_proveedor", "cambiar_estado")).thenReturn(true);
        when(servicio.conciliar(eq(FAC_ID))).thenReturn(dto("conciliada"));

        mockMvc.perform(post("/compras/facturas-proveedor/{id}/conciliar", FAC_ID)
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("conciliada"));
    }

    @Test
    void autorizarPago_devuelve422_cuandoNoConciliada() throws Exception {
        when(autorizador.tiene("factura_proveedor", "cambiar_estado")).thenReturn(true);
        when(servicio.autorizarPago(eq(FAC_ID)))
                .thenThrow(new com.dessti.crm.platform.error.ReglaNegocioException(
                        "se requiere una Factura_Proveedor conciliada para autorizar el pago."));

        mockMvc.perform(post("/compras/facturas-proveedor/{id}/autorizar-pago", FAC_ID)
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reabrir_devuelve422_cuandoNoDiscrepancia() throws Exception {
        when(autorizador.tiene("factura_proveedor", "cambiar_estado")).thenReturn(true);
        when(servicio.reabrir(eq(FAC_ID)))
                .thenThrow(new com.dessti.crm.platform.error.ReglaNegocioException(
                        "solo se puede reabrir una Factura_Proveedor en estado 'discrepancia'."));

        mockMvc.perform(post("/compras/facturas-proveedor/{id}/reabrir", FAC_ID)
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void consultar_devuelve404_cuandoNoAccesible() throws Exception {
        when(autorizador.tiene("factura_proveedor", "leer")).thenReturn(true);
        when(servicio.consultar(eq(FAC_ID)))
                .thenThrow(new com.dessti.crm.platform.error.RecursoNoEncontradoException(
                        "No se encontro la Factura_Proveedor solicitada."));

        mockMvc.perform(get("/compras/facturas-proveedor/{id}", FAC_ID).with(user("almacen")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_devuelve200_conLaPagina() throws Exception {
        when(autorizador.tiene("factura_proveedor", "listar")).thenReturn(true);
        when(servicio.listar(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto("registrada")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/compras/facturas-proveedor").with(user("almacen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].estado").value("registrada"))
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
