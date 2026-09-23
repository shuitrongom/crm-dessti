package com.dessti.crm.comercial.cliente.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.comercial.cliente.application.ClienteDto;
import com.dessti.crm.comercial.cliente.application.ContactoDto;
import com.dessti.crm.comercial.cliente.application.CrearClienteCommand;
import com.dessti.crm.comercial.cliente.application.CrearContactoCommand;
import com.dessti.crm.comercial.cliente.application.ServicioClientes;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link ClienteController} (tarea 15.2, Req 5, 12).
 *
 * <p>Se usa {@link WebMvcTest} para ejercitar la capa web sin arrancar la
 * aplicacion completa ni la base de datos: el {@link ServicioClientes} se
 * simula y el evaluador RBAC {@code @autorizador} se sustituye por un
 * {@link Autorizador} simulado, de modo que las expresiones {@code @PreAuthorize}
 * se evaluan realmente contra su resultado. Se importan {@link MethodSecurityConfig}
 * (habilita la seguridad de metodo) y {@link ManejadorGlobalErrores} (mapeo a
 * Problem Details) para reproducir el contrato REST real.</p>
 *
 * <p>Cobertura: 201 en alta, 200 en consulta, forma paginada del listado,
 * propagacion de 404, 400 por validacion de cuerpo y 403 por permiso ausente.</p>
 */
@WebMvcTest(controllers = ClienteController.class,
        properties = {
                // Valores dummy para los secretos requeridos (Req 11): el slice no
                // usa BD ni firma real. Se fijan tanto las variables de entorno
                // (placeholders de application.yml) como las rutas ya resueltas que
                // valida SecretosValidador al arranque.
                "DB_USER=test",
                "DB_PASSWORD=test",
                "JWT_SIGNING_KEY=clave-de-firma-jwt-solo-para-pruebas-0123456789",
                "CRM_ENC_KEY_ACTIVE=v1",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "crm.secretos.jwt-signing-key=clave-de-firma-jwt-solo-para-pruebas-0123456789"
        })
@Import({MethodSecurityConfig.class, ManejadorGlobalErrores.class,
        ClienteControllerTest.ConfiguracionPrueba.class})
class ClienteControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioClientes servicioClientes;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        // El gating por Plan (Req 25.4) se satisface por defecto para no romper el
        // camino feliz; el test de 403 por modulo lo anula explicitamente.
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static ClienteDto clienteDto() {
        Instant ahora = Instant.parse("2024-01-01T00:00:00Z");
        return new ClienteDto(ID, "Anuncios del Norte", "ANO120101AB1",
                "ventas@norte.mx", "5512345678",
                "Norte Signs", "moral", "5598765432",
                "Av. Reforma 100", "Monterrey", "Nuevo Leon", "64000", "Mexico",
                "Cliente preferente",
                true, 0L, ahora, ahora);
    }

    private static ContactoDto contactoDto() {
        Instant ahora = Instant.parse("2024-01-01T00:00:00Z");
        return new ContactoDto(UUID.randomUUID(), ID, "Juan Perez",
                "juan@norte.mx", "5512345678", true, 0L, ahora, ahora);
    }

    // ------------------------------------------------------------------
    // Happy paths
    // ------------------------------------------------------------------

    @Test
    void crear_devuelve201_conClienteDto() throws Exception {
        when(autorizador.tiene("cliente", "crear")).thenReturn(true);
        when(servicioClientes.crearCliente(any(CrearClienteCommand.class))).thenReturn(clienteDto());

        mockMvc.perform(post("/clientes").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Anuncios del Norte","rfc":"ANO120101AB1",
                                 "email":"ventas@norte.mx","telefono":"5512345678"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.rfc").value("ANO120101AB1"));
    }

    @Test
    void consultar_devuelveDatosBasicos_enLaRespuesta() throws Exception {
        when(autorizador.tiene("cliente", "leer")).thenReturn(true);
        when(servicioClientes.consultarCliente(ID)).thenReturn(clienteDto());

        mockMvc.perform(get("/clientes/{id}", ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreComercial").value("Norte Signs"))
                .andExpect(jsonPath("$.tipoPersona").value("moral"))
                .andExpect(jsonPath("$.telefonoAdicional").value("5598765432"))
                .andExpect(jsonPath("$.direccionCiudad").value("Monterrey"))
                .andExpect(jsonPath("$.direccionCp").value("64000"))
                .andExpect(jsonPath("$.notas").value("Cliente preferente"));
    }

    @Test
    void crear_devuelve400_cuandoTipoPersonaInvalido() throws Exception {
        when(autorizador.tiene("cliente", "crear")).thenReturn(true);

        mockMvc.perform(post("/clientes").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Anuncios del Norte","rfc":"ANO120101AB1",
                                 "telefono":"5512345678","tipoPersona":"juridica"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultar_devuelve200_conClienteDto() throws Exception {
        when(autorizador.tiene("cliente", "leer")).thenReturn(true);
        when(servicioClientes.consultarCliente(ID)).thenReturn(clienteDto());

        mockMvc.perform(get("/clientes/{id}", ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Anuncios del Norte"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("cliente", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<ClienteDto> pagina = new PageImpl<>(List.of(clienteDto()), pageable, 1);
        when(servicioClientes.listarClientes(any(), any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/clientes").param("filtro", "norte").with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void eliminar_devuelve200_conClienteDesactivado() throws Exception {
        when(autorizador.tiene("cliente", "eliminar")).thenReturn(true);
        Instant ahora = Instant.parse("2024-01-01T00:00:00Z");
        ClienteDto inactivo = new ClienteDto(ID, "Anuncios del Norte", "ANO120101AB1",
                null, null, null, null, null, null, null, null, null, null, null,
                false, 1L, ahora, ahora);
        when(servicioClientes.desactivarCliente(ID)).thenReturn(inactivo);

        mockMvc.perform(delete("/clientes/{id}", ID).with(user("ventas")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void asociarContacto_devuelve201_conContactoDto() throws Exception {
        when(autorizador.tiene("contacto", "crear")).thenReturn(true);
        when(servicioClientes.asociarContacto(any(CrearContactoCommand.class))).thenReturn(contactoDto());

        mockMvc.perform(post("/clientes/{id}/contactos", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Juan Perez","email":"juan@norte.mx","telefono":"5512345678"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteId").value(ID.toString()))
                .andExpect(jsonPath("$.nombre").value("Juan Perez"));
    }

    // ------------------------------------------------------------------
    // Error propagation and guards
    // ------------------------------------------------------------------

    @Test
    void consultar_propaga404_cuandoServicioLanzaRecursoNoEncontrado() throws Exception {
        when(autorizador.tiene("cliente", "leer")).thenReturn(true);
        when(servicioClientes.consultarCliente(ID))
                .thenThrow(new RecursoNoEncontradoException("No se encontro el Cliente solicitado."));

        mockMvc.perform(get("/clientes/{id}", ID).with(user("ventas")))
                .andExpect(status().isNotFound());
    }

    @Test
    void crear_devuelve400_cuandoNombreEnBlanco() throws Exception {
        when(autorizador.tiene("cliente", "crear")).thenReturn(true);

        mockMvc.perform(post("/clientes").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","rfc":"ANO120101AB1"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);
        when(autorizador.tiene(eq("cliente"), eq("crear"))).thenReturn(false);

        mockMvc.perform(post("/clientes").with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Anuncios del Norte","rfc":"ANO120101AB1"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_devuelve403_cuandoModuloComercialNoContratado() throws Exception {
        // El Usuario TIENE el permiso RBAC, pero su Empresa NO contrata el modulo
        // 'comercial': el gating por Plan (Req 25.4) deniega con 403 aunque el
        // permiso este presente, defensa en profundidad ademas del menu del cliente.
        when(autorizador.tiene(anyString(), anyString())).thenReturn(true);
        when(autorizador.moduloHabilitado("comercial")).thenReturn(false);

        mockMvc.perform(get("/clientes").with(user("ventas")))
                .andExpect(status().isForbidden());
    }

    /**
     * Sustituye el bean {@code autorizador} por un simulado, de modo que las
     * expresiones {@code @PreAuthorize("@autorizador.tiene(...)")} se resuelvan
     * contra un resultado controlado por cada prueba.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
