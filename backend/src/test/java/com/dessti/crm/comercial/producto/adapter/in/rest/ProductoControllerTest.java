package com.dessti.crm.comercial.producto.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.comercial.producto.application.CrearProductoCommand;
import com.dessti.crm.comercial.producto.application.ProductoDto;
import com.dessti.crm.comercial.producto.application.ServicioProductos;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link ProductoController} (tarea 16.1, Req 59,
 * 12). Reproduce el contrato REST real sin BD: {@link ServicioProductos} se
 * simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble para
 * evaluar realmente las expresiones {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = ProductoController.class,
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
        ProductoControllerTest.ConfiguracionPrueba.class})
class ProductoControllerTest {

    private static final UUID ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioProductos servicioProductos;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        // El gating por Plan (Req 25.4) se satisface por defecto para no romper el
        // camino feliz; el test de 403 por modulo lo anula explicitamente.
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static ProductoDto productoDto() {
        Instant ahora = Instant.parse("2024-01-01T00:00:00Z");
        return new ProductoDto(ID, "Pantalla LED", "pieza", "Anuncio full color",
                null, null, null, "https://cdn.example.com/pantalla.png", true, 0L, ahora, ahora);
    }

    @Test
    void crear_devuelve201_conProductoDto() throws Exception {
        when(autorizador.tiene("producto", "crear")).thenReturn(true);
        when(servicioProductos.crearProducto(any(CrearProductoCommand.class))).thenReturn(productoDto());

        mockMvc.perform(post("/productos").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pantalla LED","unidad":"pieza","descripcion":"Anuncio full color"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.nombre").value("Pantalla LED"))
                .andExpect(jsonPath("$.foto").value("https://cdn.example.com/pantalla.png"));
    }

    @Test
    void crear_propagaFotoAlComando_yLaDevuelveEnRespuesta() throws Exception {
        when(autorizador.tiene("producto", "crear")).thenReturn(true);
        when(servicioProductos.crearProducto(any(CrearProductoCommand.class))).thenReturn(productoDto());

        mockMvc.perform(post("/productos").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pantalla LED","unidad":"pieza","descripcion":"Anuncio full color",
                                 "foto":"https://cdn.example.com/pantalla.png"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foto").value("https://cdn.example.com/pantalla.png"));

        ArgumentCaptor<CrearProductoCommand> captor = ArgumentCaptor.forClass(CrearProductoCommand.class);
        verify(servicioProductos).crearProducto(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().foto())
                .isEqualTo("https://cdn.example.com/pantalla.png");
    }

    @Test
    void consultar_devuelve200_conProductoDto() throws Exception {
        when(autorizador.tiene("producto", "leer")).thenReturn(true);
        when(servicioProductos.consultarProducto(ID)).thenReturn(productoDto());

        mockMvc.perform(get("/productos/{id}", ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidad").value("pieza"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("producto", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<ProductoDto> pagina = new PageImpl<>(List.of(productoDto()), pageable, 1);
        when(servicioProductos.listarProductos(any(), any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/productos").param("filtro", "led").with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void eliminar_devuelve200_conProductoDesactivado() throws Exception {
        when(autorizador.tiene("producto", "eliminar")).thenReturn(true);
        Instant ahora = Instant.parse("2024-01-01T00:00:00Z");
        ProductoDto inactivo = new ProductoDto(ID, "Pantalla LED", "pieza", "desc",
                null, null, null, null, false, 1L, ahora, ahora);
        when(servicioProductos.desactivarProducto(ID)).thenReturn(inactivo);

        mockMvc.perform(delete("/productos/{id}", ID).with(user("ventas")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void consultar_propaga404_cuandoServicioLanzaRecursoNoEncontrado() throws Exception {
        when(autorizador.tiene("producto", "leer")).thenReturn(true);
        when(servicioProductos.consultarProducto(ID))
                .thenThrow(new RecursoNoEncontradoException("No se encontro el Producto solicitado."));

        mockMvc.perform(get("/productos/{id}", ID).with(user("ventas")))
                .andExpect(status().isNotFound());
    }

    @Test
    void crear_devuelve400_cuandoDescripcionEnBlanco() throws Exception {
        when(autorizador.tiene("producto", "crear")).thenReturn(true);

        mockMvc.perform(post("/productos").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pantalla LED","unidad":"pieza","descripcion":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);
        when(autorizador.tiene(eq("producto"), eq("crear"))).thenReturn(false);

        mockMvc.perform(post("/productos").with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pantalla LED","unidad":"pieza","descripcion":"desc"}
                                """))
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
