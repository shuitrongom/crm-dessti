package com.dessti.crm.comercial.oportunidad.adapter.in.rest;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.comercial.oportunidad.application.CrearOportunidadCommand;
import com.dessti.crm.comercial.oportunidad.application.OportunidadDto;
import com.dessti.crm.comercial.oportunidad.application.ServicioOportunidades;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link OportunidadController} (tarea 17.1,
 * Req 14, 12). Reproduce el contrato REST real sin BD: {@link ServicioOportunidades}
 * se simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble
 * para evaluar realmente las expresiones {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = OportunidadController.class,
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
        OportunidadControllerTest.ConfiguracionPrueba.class})
class OportunidadControllerTest {

    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioOportunidades servicioOportunidades;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        // El gating por Plan (Req 25.4) se satisface por defecto para no romper el
        // camino feliz; el test de 403 por modulo lo anula explicitamente.
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    private static OportunidadDto oportunidadDto(String etapa) {
        Instant ahora = Instant.parse("2024-01-01T00:00:00Z");
        return new OportunidadDto(ID, CLIENTE, "Anuncio corporativo", new BigDecimal("15000.00"),
                etapa, null, null, null, 0L, ahora, ahora);
    }

    @Test
    void crear_devuelve201_conOportunidadDto() throws Exception {
        when(autorizador.tiene("oportunidad", "crear")).thenReturn(true);
        when(servicioOportunidades.crearOportunidad(any(CrearOportunidadCommand.class)))
                .thenReturn(oportunidadDto("nuevo"));

        mockMvc.perform(post("/oportunidades").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":"22222222-2222-2222-2222-222222222222",
                                 "titulo":"Anuncio corporativo","valorEstimado":15000.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.etapa").value("nuevo"));
    }

    @Test
    void cambiarEtapa_devuelve200_conNuevaEtapa() throws Exception {
        when(autorizador.tiene("oportunidad", "cambiar_estado")).thenReturn(true);
        when(servicioOportunidades.cambiarEtapa(eq(ID), eq("calificado")))
                .thenReturn(oportunidadDto("calificado"));

        mockMvc.perform(put("/oportunidades/{id}/etapa", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etapa":"calificado"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapa").value("calificado"));
    }

    @Test
    void cambiarEtapa_propaga409_cuandoTransicionInvalida() throws Exception {
        when(autorizador.tiene("oportunidad", "cambiar_estado")).thenReturn(true);
        when(servicioOportunidades.cambiarEtapa(eq(ID), eq("ganado")))
                .thenThrow(new TransicionInvalidaException("Transicion de etapa invalida."));

        mockMvc.perform(put("/oportunidades/{id}/etapa", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etapa":"ganado"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void asignarResponsable_devuelve200() throws Exception {
        when(autorizador.tiene("oportunidad", "actualizar")).thenReturn(true);
        when(servicioOportunidades.asignarResponsable(eq(ID), any(UUID.class)))
                .thenReturn(oportunidadDto("nuevo"));

        mockMvc.perform(put("/oportunidades/{id}/responsable", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId":"33333333-3333-3333-3333-333333333333"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void asignarCanalVenta_devuelve200() throws Exception {
        when(autorizador.tiene("oportunidad", "actualizar")).thenReturn(true);
        when(servicioOportunidades.asignarCanalVenta(eq(ID), any(UUID.class)))
                .thenReturn(oportunidadDto("nuevo"));

        mockMvc.perform(put("/oportunidades/{id}/canal-venta", ID).with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canalVentaId":"66666666-6666-6666-6666-666666666666"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void convertir_devuelve422_cuandoNoGanado() throws Exception {
        when(autorizador.tiene("oportunidad", "actualizar")).thenReturn(true);
        when(servicioOportunidades.convertirEnCotizacion(ID))
                .thenThrow(new ReglaNegocioException("Se requiere una Oportunidad en etapa 'ganado'."));

        mockMvc.perform(post("/oportunidades/{id}/convertir", ID).with(user("ventas")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void convertir_devuelve200_conCotizacionId() throws Exception {
        when(autorizador.tiene("oportunidad", "actualizar")).thenReturn(true);
        UUID cotizacion = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(servicioOportunidades.convertirEnCotizacion(ID)).thenReturn(cotizacion);

        mockMvc.perform(post("/oportunidades/{id}/convertir", ID).with(user("ventas")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cotizacionId").value(cotizacion.toString()));
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("oportunidad", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<OportunidadDto> pagina = new PageImpl<>(List.of(oportunidadDto("nuevo")), pageable, 1);
        when(servicioOportunidades.listarOportunidades(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(pagina);

        mockMvc.perform(get("/oportunidades").param("etapa", "nuevo").with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void crear_devuelve400_cuandoValorFueraDeRango() throws Exception {
        when(autorizador.tiene("oportunidad", "crear")).thenReturn(true);

        mockMvc.perform(post("/oportunidades").with(user("ventas")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":"22222222-2222-2222-2222-222222222222",
                                 "titulo":"Anuncio","valorEstimado":0.00}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/oportunidades").with(user("sin_permiso")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":"22222222-2222-2222-2222-222222222222",
                                 "titulo":"Anuncio","valorEstimado":15000.00}
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
