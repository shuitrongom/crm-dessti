package com.dessti.crm.comercial.cotizacion.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.comercial.cotizacion.application.CotizacionDto;
import com.dessti.crm.comercial.cotizacion.application.CrearCotizacionCommand;
import com.dessti.crm.comercial.cotizacion.application.PartidaCotizacionDto;
import com.dessti.crm.comercial.cotizacion.application.ServicioCotizaciones;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link CotizacionController} (tarea 17.2, Req 6,
 * 12). Reproduce el contrato REST real sin BD: {@link ServicioCotizaciones} se
 * simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble para
 * evaluar realmente las expresiones {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = CotizacionController.class,
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
        CotizacionControllerTest.ConfiguracionPrueba.class})
class CotizacionControllerTest {

    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioCotizaciones servicioCotizaciones;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        // El gating por Plan (Req 25.4) se satisface por defecto para no romper el
        // camino feliz; el test de 403 por modulo lo anula explicitamente.
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static CotizacionDto cotizacionDto(String estado) {
        return cotizacionDto(estado, false);
    }

    private static CotizacionDto cotizacionDto(String estado, boolean emisorIncompleto) {
        Instant ahora = Instant.parse("2024-01-01T00:00:00Z");
        PartidaCotizacionDto partida = new PartidaCotizacionDto(
                UUID.randomUUID(), null, "Anuncio", 2, new BigDecimal("100.00"), new BigDecimal("200.00"));
        return new CotizacionDto(ID, "COT-2026-0001", CLIENTE, "Anuncios ACME", "AAA010101AAA",
                "ventas@acme.mx", null, estado, new BigDecimal("200.00"), new BigDecimal("200.00"),
                "MXN", java.time.LocalDate.of(2026, 1, 15), null, null, null, null,
                List.of(partida), null, 0L, ahora, ahora, emisorIncompleto);
    }

    @Test
    void crear_devuelve201_conCotizacionDto() throws Exception {
        when(autorizador.tiene("cotizacion", "crear")).thenReturn(true);
        when(servicioCotizaciones.crearCotizacion(any(CrearCotizacionCommand.class)))
                .thenReturn(cotizacionDto("borrador"));

        mockMvc.perform(post("/cotizaciones").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":"22222222-2222-2222-2222-222222222222",
                                 "partidas":[{"descripcion":"Anuncio","cantidad":2,"precioUnitario":100.00}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.estado").value("borrador"))
                .andExpect(jsonPath("$.total").value(200.00));
    }

    @Test
    void crear_devuelve400_cuandoSinPartidas() throws Exception {
        when(autorizador.tiene("cotizacion", "crear")).thenReturn(true);

        mockMvc.perform(post("/cotizaciones").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":"22222222-2222-2222-2222-222222222222","partidas":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_devuelve400_cuandoCantidadFueraDeRango() throws Exception {
        when(autorizador.tiene("cotizacion", "crear")).thenReturn(true);

        mockMvc.perform(post("/cotizaciones").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":"22222222-2222-2222-2222-222222222222",
                                 "partidas":[{"descripcion":"Anuncio","cantidad":0,"precioUnitario":100.00}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agregarPartida_devuelve200() throws Exception {
        when(autorizador.tiene("cotizacion", "actualizar")).thenReturn(true);
        when(servicioCotizaciones.agregarPartida(eq(ID), any())).thenReturn(cotizacionDto("borrador"));

        mockMvc.perform(post("/cotizaciones/{id}/partidas", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Extra","cantidad":3,"precioUnitario":10.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("borrador"));
    }

    @Test
    void cambiarEstado_devuelve200_conNuevoEstado() throws Exception {
        when(autorizador.tiene("cotizacion", "cambiar_estado")).thenReturn(true);
        when(servicioCotizaciones.cambiarEstado(eq(ID), eq("enviada")))
                .thenReturn(cotizacionDto("enviada"));

        mockMvc.perform(put("/cotizaciones/{id}/estado", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estado":"enviada"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("enviada"));
    }

    @Test
    void cambiarEstado_propaga409_cuandoTransicionInvalida() throws Exception {
        when(autorizador.tiene("cotizacion", "cambiar_estado")).thenReturn(true);
        when(servicioCotizaciones.cambiarEstado(eq(ID), eq("aprobada")))
                .thenThrow(new TransicionInvalidaException("Transicion de estado invalida."));

        mockMvc.perform(put("/cotizaciones/{id}/estado", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estado":"aprobada"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void asignarCanalVenta_devuelve200() throws Exception {
        when(autorizador.tiene("cotizacion", "actualizar")).thenReturn(true);
        when(servicioCotizaciones.asignarCanalVenta(eq(ID), any(UUID.class)))
                .thenReturn(cotizacionDto("borrador"));

        mockMvc.perform(put("/cotizaciones/{id}/canal-venta", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canalVentaId":"66666666-6666-6666-6666-666666666666"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("borrador"));
    }

    @Test
    void agregarPartida_propaga422_cuandoReglaNegocio() throws Exception {
        when(autorizador.tiene("cotizacion", "actualizar")).thenReturn(true);
        when(servicioCotizaciones.agregarPartida(eq(ID), any()))
                .thenThrow(new ReglaNegocioException("Solo en 'borrador'."));

        mockMvc.perform(post("/cotizaciones/{id}/partidas", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Extra","cantidad":1,"precioUnitario":10.00}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("cotizacion", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<CotizacionDto> pagina = new PageImpl<>(List.of(cotizacionDto("borrador")), pageable, 1);
        when(servicioCotizaciones.listarCotizaciones(any(), any(), any(), any(Pageable.class)))
                .thenReturn(pagina);

        mockMvc.perform(get("/cotizaciones").param("estado", "borrador").with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/cotizaciones").with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":"22222222-2222-2222-2222-222222222222",
                                 "partidas":[{"descripcion":"Anuncio","cantidad":2,"precioUnitario":100.00}]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultar_exponeFolioYDatosCliente() throws Exception {
        when(autorizador.tiene("cotizacion", "leer")).thenReturn(true);
        when(servicioCotizaciones.consultarCotizacion(ID)).thenReturn(cotizacionDto("borrador"));

        mockMvc.perform(get("/cotizaciones/{id}", ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folio").value("COT-2026-0001"))
                .andExpect(jsonPath("$.clienteNombre").value("Anuncios ACME"))
                .andExpect(jsonPath("$.clienteEmail").value("ventas@acme.mx"));
    }

    @Test
    void descargarPdf_devuelve200PdfInline_conFolioEnNombre() throws Exception {
        when(autorizador.tiene("cotizacion", "leer")).thenReturn(true);
        when(servicioCotizaciones.consultarCotizacion(ID)).thenReturn(cotizacionDto("borrador"));
        when(servicioCotizaciones.generarPdf(ID))
                .thenReturn(new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4'});

        mockMvc.perform(get("/cotizaciones/{id}/pdf", ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("inline")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("cotizacion-COT-2026-0001.pdf")));
    }

    @Test
    void descargarPdf_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/cotizaciones/{id}/pdf", ID).with(user("sin_permiso")))
                .andExpect(status().isForbidden());
    }

    @Test
    void enviarCorreo_devuelve200_conDtoActualizado() throws Exception {
        when(autorizador.tiene("cotizacion", "actualizar")).thenReturn(true);
        when(servicioCotizaciones.enviarPorCorreo(eq(ID), any()))
                .thenReturn(cotizacionDto("enviada"));

        mockMvc.perform(post("/cotizaciones/{id}/enviar-correo", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cliente@correo.mx"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("enviada"));
    }

    @Test
    void enviarCorreo_exponeEmisorIncompleto_cuandoServicioLoReporta() throws Exception {
        when(autorizador.tiene("cotizacion", "actualizar")).thenReturn(true);
        when(servicioCotizaciones.enviarPorCorreo(eq(ID), any()))
                .thenReturn(cotizacionDto("enviada", true));

        mockMvc.perform(post("/cotizaciones/{id}/enviar-correo", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emisorIncompleto").value(true));
    }

    @Test
    void enviarCorreo_emisorIncompletoFalse_cuandoServicioNoLoReporta() throws Exception {
        when(autorizador.tiene("cotizacion", "actualizar")).thenReturn(true);
        when(servicioCotizaciones.enviarPorCorreo(eq(ID), any()))
                .thenReturn(cotizacionDto("enviada", false));

        mockMvc.perform(post("/cotizaciones/{id}/enviar-correo", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emisorIncompleto").value(false));
    }

    @Test
    void descargarPdf_anexaEncabezadoEmisorIncompleto_cuandoIncompleto() throws Exception {
        when(autorizador.tiene("cotizacion", "leer")).thenReturn(true);
        when(servicioCotizaciones.consultarCotizacion(ID)).thenReturn(cotizacionDto("borrador"));
        when(servicioCotizaciones.generarPdf(ID)).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
        when(servicioCotizaciones.emisorIncompleto()).thenReturn(true);

        mockMvc.perform(get("/cotizaciones/{id}/pdf", ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Emisor-Incompleto", "true"));
    }

    @Test
    void descargarPdf_omiteEncabezadoEmisorIncompleto_cuandoCompleto() throws Exception {
        when(autorizador.tiene("cotizacion", "leer")).thenReturn(true);
        when(servicioCotizaciones.consultarCotizacion(ID)).thenReturn(cotizacionDto("borrador"));
        when(servicioCotizaciones.generarPdf(ID)).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
        when(servicioCotizaciones.emisorIncompleto()).thenReturn(false);

        mockMvc.perform(get("/cotizaciones/{id}/pdf", ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Emisor-Incompleto"));
    }

    @Test
    void enviarCorreo_devuelve422_cuandoSinCorreo() throws Exception {
        when(autorizador.tiene("cotizacion", "actualizar")).thenReturn(true);
        when(servicioCotizaciones.enviarPorCorreo(eq(ID), any()))
                .thenThrow(new ReglaNegocioException("El cliente no tiene correo; indique uno."));

        mockMvc.perform(post("/cotizaciones/{id}/enviar-correo", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void enviarCorreo_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/cotizaciones/{id}/enviar-correo", ID).with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
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
