package com.dessti.crm.operacion.inventario.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.operacion.inventario.application.MaterialDto;
import com.dessti.crm.operacion.inventario.application.MovimientoInventarioDto;
import com.dessti.crm.operacion.inventario.application.ServicioInventario;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link MaterialController} (tarea 20.1, Req 18, 12).
 * Reproduce el contrato REST real sin BD: {@link ServicioInventario} se simula y el
 * evaluador RBAC {@code @autorizador} se sustituye por un doble para evaluar realmente
 * las expresiones {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = MaterialController.class,
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
        MaterialControllerTest.ConfiguracionPrueba.class})
class MaterialControllerTest {

    private static final UUID ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioInventario servicioInventario;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        // El gating por Plan (Req 25.4) se satisface por defecto para no romper el
        // camino feliz; el test de 403 por modulo lo anula explicitamente.
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static MaterialDto materialDto(BigDecimal existencias, boolean stockBajo) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new MaterialDto(ID, "Tubo LED", "pieza", new BigDecimal("5"),
                existencias, stockBajo, true, 0L, ahora, ahora);
    }

    private static MovimientoInventarioDto movimientoDto(String tipo, BigDecimal resultantes) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new MovimientoInventarioDto(
                UUID.fromString("66666666-6666-6666-6666-666666666666"), ID, tipo,
                new BigDecimal("4"), resultantes, null, null, 0L, ahora);
    }

    @Test
    void crear_devuelve201_conExistenciasCero() throws Exception {
        when(autorizador.tiene("material", "crear")).thenReturn(true);
        when(servicioInventario.crearMaterial(eq("Tubo LED"), eq("pieza"), any(BigDecimal.class)))
                .thenReturn(materialDto(new BigDecimal("0"), false));

        mockMvc.perform(post("/materiales")
                        .contentType("application/json")
                        .content("{\"nombre\":\"Tubo LED\",\"unidadMedida\":\"pieza\",\"stockMinimo\":5}")
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.existencias").value(0))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void consultar_devuelve200() throws Exception {
        when(autorizador.tiene("material", "leer")).thenReturn(true);
        when(servicioInventario.consultar(eq(ID))).thenReturn(materialDto(new BigDecimal("3"), false));

        mockMvc.perform(get("/materiales/{id}", ID).with(user("almacen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void consultar_devuelve404_cuandoNoAccesible() throws Exception {
        when(autorizador.tiene("material", "leer")).thenReturn(true);
        when(servicioInventario.consultar(eq(ID)))
                .thenThrow(new RecursoNoEncontradoException("No se encontro el Material solicitado."));

        mockMvc.perform(get("/materiales/{id}", ID).with(user("almacen")))
                .andExpect(status().isNotFound());
    }

    @Test
    void registrarMovimiento_devuelve201() throws Exception {
        when(autorizador.tiene("movimiento_inventario", "crear")).thenReturn(true);
        when(servicioInventario.registrarMovimiento(eq(ID), eq("salida"), any(BigDecimal.class), any()))
                .thenReturn(movimientoDto("salida", new BigDecimal("6")));

        mockMvc.perform(post("/materiales/{id}/movimientos", ID)
                        .contentType("application/json")
                        .content("{\"tipo\":\"salida\",\"cantidad\":4}")
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("salida"))
                .andExpect(jsonPath("$.existenciasResultantes").value(6));
    }

    @Test
    void registrarMovimiento_propaga422_cuandoExistenciasInsuficientes() throws Exception {
        when(autorizador.tiene("movimiento_inventario", "crear")).thenReturn(true);
        when(servicioInventario.registrarMovimiento(eq(ID), eq("salida"), any(BigDecimal.class), any()))
                .thenThrow(new ReglaNegocioException("existencias insuficientes"));

        mockMvc.perform(post("/materiales/{id}/movimientos", ID)
                        .contentType("application/json")
                        .content("{\"tipo\":\"salida\",\"cantidad\":999}")
                        .with(user("almacen")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("material", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<MaterialDto> pagina =
                new PageImpl<>(List.of(materialDto(new BigDecimal("1"), true)), pageable, 1);
        when(servicioInventario.listarMateriales(any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(pagina);

        mockMvc.perform(get("/materiales").param("stockBajo", "true").with(user("almacen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].stockBajo").value(true));
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/materiales")
                        .contentType("application/json")
                        .content("{\"nombre\":\"Tubo LED\",\"unidadMedida\":\"pieza\",\"stockMinimo\":5}")
                        .with(user("sin_permiso")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_devuelve403_cuandoModuloOperacionNoContratado() throws Exception {
        // El Usuario TIENE el permiso RBAC, pero su Empresa NO contrata el modulo
        // 'operacion': el gating por Plan (Req 25.4) deniega con 403.
        when(autorizador.tiene(anyString(), anyString())).thenReturn(true);
        when(autorizador.moduloHabilitado("operacion")).thenReturn(false);

        mockMvc.perform(get("/materiales").with(user("almacen")))
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
