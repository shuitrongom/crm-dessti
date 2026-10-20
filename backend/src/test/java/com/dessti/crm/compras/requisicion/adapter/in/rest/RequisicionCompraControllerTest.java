package com.dessti.crm.compras.requisicion.adapter.in.rest;

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

import com.dessti.crm.compras.requisicion.application.PartidaRequisicionDto;
import com.dessti.crm.compras.requisicion.application.RequisicionCompraDto;
import com.dessti.crm.compras.requisicion.application.ServicioRequisiciones;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link RequisicionCompraController} (Req 30). El
 * {@link ServicioRequisiciones} se simula y el {@code @autorizador} se sustituye por un
 * doble para evaluar realmente las expresiones {@code @PreAuthorize}, incluida la SpEL de
 * segregación de funciones (V75): aprobar/rechazar exige {@code requisicion_compra:aprobar}
 * y enviar/cancelar {@code requisicion_compra:cambiar_estado}.
 */
@WebMvcTest(controllers = RequisicionCompraController.class,
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
        RequisicionCompraControllerTest.ConfiguracionPrueba.class})
class RequisicionCompraControllerTest {

    private static final UUID REQ_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID MATERIAL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROVEEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioRequisiciones servicio;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        org.mockito.Mockito.lenient()
                .when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static RequisicionCompraDto dto(String estado, UUID ordenCompraId) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new RequisicionCompraDto(REQ_ID, estado, ordenCompraId,
                List.of(new PartidaRequisicionDto(
                        UUID.fromString("88888888-8888-8888-8888-888888888888"), MATERIAL_ID, 5)),
                0L, ahora, ahora);
    }

    @Test
    void crear_devuelve201() throws Exception {
        when(autorizador.tiene("requisicion_compra", "crear")).thenReturn(true);
        when(servicio.crearRequisicion(any())).thenReturn(dto("borrador", null));

        mockMvc.perform(post("/compras/requisiciones")
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidas\":[{\"materialId\":\"" + MATERIAL_ID + "\",\"cantidad\":5}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("borrador"));
    }

    @Test
    void enviar_usaPermisoCambiarEstado_y_devuelve200() throws Exception {
        when(autorizador.tiene("requisicion_compra", "cambiar_estado")).thenReturn(true);
        when(servicio.cambiarEstado(eq(REQ_ID), eq("enviada"))).thenReturn(dto("enviada", null));

        mockMvc.perform(put("/compras/requisiciones/{id}/estado", REQ_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"enviada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("enviada"));
    }

    @Test
    void cancelar_usaPermisoCambiarEstado_y_devuelve200() throws Exception {
        when(autorizador.tiene("requisicion_compra", "cambiar_estado")).thenReturn(true);
        when(servicio.cambiarEstado(eq(REQ_ID), eq("cancelada"))).thenReturn(dto("cancelada", null));

        mockMvc.perform(put("/compras/requisiciones/{id}/estado", REQ_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"cancelada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cancelada"));
    }

    @Test
    void aprobar_sinPermisoAprobar_devuelve403_aunqueTengaCambiarEstado() throws Exception {
        // Segregación V75: tener solo 'cambiar_estado' NO permite aprobar.
        when(autorizador.tiene("requisicion_compra", "cambiar_estado")).thenReturn(true);
        when(autorizador.tiene("requisicion_compra", "aprobar")).thenReturn(false);

        mockMvc.perform(put("/compras/requisiciones/{id}/estado", REQ_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"aprobada\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aprobar_conPermisoAprobar_devuelve200() throws Exception {
        when(autorizador.tiene("requisicion_compra", "aprobar")).thenReturn(true);
        when(servicio.cambiarEstado(eq(REQ_ID), eq("aprobada"))).thenReturn(dto("aprobada", null));

        mockMvc.perform(put("/compras/requisiciones/{id}/estado", REQ_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"aprobada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("aprobada"));
    }

    @Test
    void cambiarEstado_transicionInvalida_devuelve409() throws Exception {
        when(autorizador.tiene("requisicion_compra", "aprobar")).thenReturn(true);
        when(servicio.cambiarEstado(eq(REQ_ID), eq("aprobada")))
                .thenThrow(new TransicionInvalidaException("Transicion invalida."));

        mockMvc.perform(put("/compras/requisiciones/{id}/estado", REQ_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"aprobada\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void generarOrden_devuelve201_conLaOrdenEnlazada() throws Exception {
        UUID ordenId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(autorizador.tiene("requisicion_compra", "cambiar_estado")).thenReturn(true);
        when(servicio.generarOrdenCompra(eq(REQ_ID), any())).thenReturn(dto("aprobada", ordenId));

        mockMvc.perform(post("/compras/requisiciones/{id}/orden-compra", REQ_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedorId\":\"" + PROVEEDOR_ID + "\",\"partidas\":[{\"materialId\":\""
                                + MATERIAL_ID + "\",\"cantidad\":5,\"precioUnitario\":100.00}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordenCompraId").value(ordenId.toString()));
    }

    @Test
    void generarOrden_noAprobada_devuelve422() throws Exception {
        when(autorizador.tiene("requisicion_compra", "cambiar_estado")).thenReturn(true);
        when(servicio.generarOrdenCompra(eq(REQ_ID), any()))
                .thenThrow(new ReglaNegocioException("se requiere una Requisicion_Compra aprobada"));

        mockMvc.perform(post("/compras/requisiciones/{id}/orden-compra", REQ_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedorId\":\"" + PROVEEDOR_ID + "\",\"partidas\":[{\"materialId\":\""
                                + MATERIAL_ID + "\",\"cantidad\":5,\"precioUnitario\":100.00}]}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listar_devuelve200_conLaPagina() throws Exception {
        when(autorizador.tiene("requisicion_compra", "listar")).thenReturn(true);
        when(servicio.listarRequisiciones(any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto("borrador", null)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/compras/requisiciones").with(user("compras")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].estado").value("borrador"))
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
