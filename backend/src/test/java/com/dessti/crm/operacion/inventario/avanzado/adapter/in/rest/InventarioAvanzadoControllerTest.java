package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.operacion.inventario.avanzado.application.AlmacenDto;
import com.dessti.crm.operacion.inventario.avanzado.application.ConfigInventarioMaterialDto;
import com.dessti.crm.operacion.inventario.avanzado.application.LoteDto;
import com.dessti.crm.operacion.inventario.avanzado.application.MovimientoAlmacenDto;
import com.dessti.crm.operacion.inventario.avanzado.application.ServicioInventarioAvanzado;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
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

    private static final UUID MATERIAL_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static MovimientoAlmacenDto movimientoDto() {
        Instant ahora = Instant.parse("2024-05-02T10:00:00Z");
        return new MovimientoAlmacenDto(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                ALMACEN_ID, MATERIAL_ID, null, "entrada",
                new BigDecimal("10.000"), new BigDecimal("5.0000"), new BigDecimal("50.0000"),
                new BigDecimal("10.000"), new BigDecimal("50.0000"), null, null, 0L, ahora);
    }

    @Test
    void consultarKardex_sinRangoDeFechas_devuelve200_yPasaFechasNulasAlServicio() throws Exception {
        when(autorizador.tiene("kardex", "leer")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<MovimientoAlmacenDto> pagina = new PageImpl<>(List.of(movimientoDto()), pageable, 1);
        // El controller no envia desde/hasta cuando no vienen en la peticion: el
        // servicio los recibe null (caso que disparaba el error del bind de fecha).
        when(servicio.consultarKardex(eq(ALMACEN_ID), eq(MATERIAL_ID), isNull(), isNull(),
                any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get(
                        "/inventario-avanzado/almacenes/{almacenId}/materiales/{materialId}/kardex",
                        ALMACEN_ID, MATERIAL_ID).with(user("almacen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("entrada"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void consultarKardex_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get(
                        "/inventario-avanzado/almacenes/{almacenId}/materiales/{materialId}/kardex",
                        ALMACEN_ID, MATERIAL_ID).with(user("sin_permiso")))
                .andExpect(status().isForbidden());
    }

    private static ConfigInventarioMaterialDto configDto() {
        Instant ahora = Instant.parse("2024-05-02T10:00:00Z");
        // puntoReorden = consumo(2) * dias(3) + seguridad(4) = 10
        return new ConfigInventarioMaterialDto(
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                MATERIAL_ID, "promedio", new BigDecimal("100.000"), false,
                new BigDecimal("2.000"), 3, new BigDecimal("4.000"),
                new BigDecimal("10.000"), 0L, ahora, ahora);
    }

    @Test
    void consultarConfigInventario_devuelve200_conElPuntoDeReordenDerivado() throws Exception {
        when(autorizador.tiene("material", "leer")).thenReturn(true);
        when(servicio.consultarConfigInventario(eq(MATERIAL_ID))).thenReturn(configDto());

        mockMvc.perform(get("/inventario-avanzado/materiales/{materialId}/config-inventario",
                        MATERIAL_ID).with(user("almacen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materialId").value(MATERIAL_ID.toString()))
                .andExpect(jsonPath("$.metodoCosteo").value("promedio"))
                .andExpect(jsonPath("$.puntoReorden").value(10.0));
    }

    @Test
    void consultarConfigInventario_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/inventario-avanzado/materiales/{materialId}/config-inventario",
                        MATERIAL_ID).with(user("sin_permiso")))
                .andExpect(status().isForbidden());
    }

    private static final UUID LOTE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private static LoteDto loteDto(LocalDate caducidad) {
        Instant ahora = Instant.parse("2024-05-02T10:00:00Z");
        return new LoteDto(LOTE_ID, MATERIAL_ID, "L-001", caducidad, 0L, ahora, ahora);
    }

    @Test
    void actualizarLote_devuelve200_conLaNuevaCaducidad() throws Exception {
        when(autorizador.tiene("lote", "actualizar")).thenReturn(true);
        when(servicio.actualizarLote(eq(LOTE_ID), eq(LocalDate.parse("2027-01-31"))))
                .thenReturn(loteDto(LocalDate.parse("2027-01-31")));

        mockMvc.perform(put("/inventario-avanzado/lotes/{id}", LOTE_ID)
                        .with(user("almacen")).with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fechaCaducidad\":\"2027-01-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LOTE_ID.toString()))
                .andExpect(jsonPath("$.fechaCaducidad").value("2027-01-31"));
    }

    @Test
    void actualizarLote_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(put("/inventario-avanzado/lotes/{id}", LOTE_ID)
                        .with(user("sin_permiso")).with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fechaCaducidad\":null}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminarLote_devuelve204_cuandoNoEstaEnUso() throws Exception {
        when(autorizador.tiene("lote", "eliminar")).thenReturn(true);
        doNothing().when(servicio).eliminarLote(eq(LOTE_ID));

        mockMvc.perform(delete("/inventario-avanzado/lotes/{id}", LOTE_ID)
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminarLote_devuelve422_cuandoElLoteEstaEnUso() throws Exception {
        when(autorizador.tiene("lote", "eliminar")).thenReturn(true);
        doThrow(new ReglaNegocioException("No se puede eliminar el Lote 'L-001': tiene movimientos."))
                .when(servicio).eliminarLote(eq(LOTE_ID));

        mockMvc.perform(delete("/inventario-avanzado/lotes/{id}", LOTE_ID)
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void eliminarLote_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(delete("/inventario-avanzado/lotes/{id}", LOTE_ID)
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
