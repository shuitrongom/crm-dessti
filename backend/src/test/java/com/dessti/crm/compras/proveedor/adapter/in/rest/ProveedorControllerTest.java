package com.dessti.crm.compras.proveedor.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.dessti.crm.compras.proveedor.application.ProveedorDto;
import com.dessti.crm.compras.proveedor.application.ServicioProveedores;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link ProveedorController} (Req 29). El
 * {@link ServicioProveedores} se simula y el evaluador RBAC {@code @autorizador} se
 * sustituye por un doble para evaluar realmente las expresiones {@code @PreAuthorize}
 * (modulo compras + permiso atomico). Verifican los codigos de estado y el mapeo del
 * contrato REST (201/200/404/409/422/403), incluidos los campos fiscales de V89.
 */
@WebMvcTest(controllers = ProveedorController.class,
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
        ProveedorControllerTest.ConfiguracionPrueba.class})
class ProveedorControllerTest {

    private static final UUID PROVEEDOR_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String RFC = "ABC010101AB1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioProveedores servicio;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        org.mockito.Mockito.lenient()
                .when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static ProveedorDto proveedorDto(boolean activo) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new ProveedorDto(PROVEEDOR_ID, "Aceros del Norte", RFC,
                "compras@aceros.mx", "5512345678", "Juan Perez", "601", 30,
                "Av. Reforma 100", "Monterrey", "Nuevo Leon", "64000",
                activo, 0L, ahora, ahora);
    }

    private static String cuerpoValido() {
        return "{\"nombre\":\"Aceros del Norte\",\"rfc\":\"" + RFC + "\","
                + "\"email\":\"compras@aceros.mx\",\"telefono\":\"5512345678\","
                + "\"personaContacto\":\"Juan Perez\",\"regimenFiscal\":\"601\","
                + "\"diasCredito\":30,\"domicilioCalle\":\"Av. Reforma 100\","
                + "\"domicilioCiudad\":\"Monterrey\",\"domicilioEstado\":\"Nuevo Leon\","
                + "\"codigoPostal\":\"64000\"}";
    }

    @Test
    void crear_devuelve201_conLosDatosFiscales() throws Exception {
        when(autorizador.tiene("proveedor", "crear")).thenReturn(true);
        when(servicio.crearProveedor(any())).thenReturn(proveedorDto(true));

        mockMvc.perform(post("/compras/proveedores")
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rfc").value(RFC))
                .andExpect(jsonPath("$.diasCredito").value(30))
                .andExpect(jsonPath("$.regimenFiscal").value("601"))
                .andExpect(jsonPath("$.codigoPostal").value("64000"));
    }

    @Test
    void crear_devuelve409_cuandoRfcDuplicado() throws Exception {
        when(autorizador.tiene("proveedor", "crear")).thenReturn(true);
        when(servicio.crearProveedor(any()))
                .thenThrow(new ConflictoUnicidadException("RFC duplicado"));

        mockMvc.perform(post("/compras/proveedores")
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isConflict());
    }

    @Test
    void crear_devuelve422_cuandoDatosInvalidos() throws Exception {
        when(autorizador.tiene("proveedor", "crear")).thenReturn(true);
        when(servicio.crearProveedor(any()))
                .thenThrow(new ReglaNegocioException("El formato del identificador fiscal (RFC) es invalido."));

        mockMvc.perform(post("/compras/proveedores")
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/compras/proveedores")
                        .with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizar_devuelve200() throws Exception {
        when(autorizador.tiene("proveedor", "actualizar")).thenReturn(true);
        when(servicio.actualizarProveedor(eq(PROVEEDOR_ID), any())).thenReturn(proveedorDto(true));

        mockMvc.perform(put("/compras/proveedores/{id}", PROVEEDOR_ID)
                        .with(user("compras")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROVEEDOR_ID.toString()));
    }

    @Test
    void desactivar_devuelve200_conProveedorInactivo() throws Exception {
        when(autorizador.tiene("proveedor", "actualizar")).thenReturn(true);
        when(servicio.desactivarProveedor(eq(PROVEEDOR_ID))).thenReturn(proveedorDto(false));

        mockMvc.perform(delete("/compras/proveedores/{id}", PROVEEDOR_ID)
                        .with(user("compras")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void reactivar_devuelve200_conProveedorActivo() throws Exception {
        when(autorizador.tiene("proveedor", "actualizar")).thenReturn(true);
        when(servicio.reactivarProveedor(eq(PROVEEDOR_ID))).thenReturn(proveedorDto(true));

        mockMvc.perform(put("/compras/proveedores/{id}/activar", PROVEEDOR_ID)
                        .with(user("compras")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void reactivar_devuelve409_cuandoOtroActivoUsaElRfc() throws Exception {
        when(autorizador.tiene("proveedor", "actualizar")).thenReturn(true);
        when(servicio.reactivarProveedor(eq(PROVEEDOR_ID)))
                .thenThrow(new ConflictoUnicidadException("RFC en uso por otro activo"));

        mockMvc.perform(put("/compras/proveedores/{id}/activar", PROVEEDOR_ID)
                        .with(user("compras")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void consultar_devuelve404_cuandoNoAccesible() throws Exception {
        when(autorizador.tiene("proveedor", "leer")).thenReturn(true);
        when(servicio.consultarProveedor(eq(PROVEEDOR_ID)))
                .thenThrow(new RecursoNoEncontradoException("No se encontro el Proveedor solicitado."));

        mockMvc.perform(get("/compras/proveedores/{id}", PROVEEDOR_ID).with(user("compras")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_devuelve200_conLaPagina() throws Exception {
        when(autorizador.tiene("proveedor", "listar")).thenReturn(true);
        when(servicio.listarProveedores(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(proveedorDto(true)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/compras/proveedores").with(user("compras")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rfc").value(RFC))
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
