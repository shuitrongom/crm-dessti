package com.dessti.crm.vertical.anuncios.pruebadiseno.adapter.in.rest;

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

import com.dessti.crm.vertical.anuncios.pruebadiseno.application.PruebaDisenoDto;
import com.dessti.crm.vertical.anuncios.pruebadiseno.application.ResultadoRechazoPruebaDiseno;
import com.dessti.crm.vertical.anuncios.pruebadiseno.application.ServicioPruebasDiseno;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link PruebaDisenoController} (tarea 18.1,
 * Req 15, 12). Reproduce el contrato REST real sin BD: {@link ServicioPruebasDiseno}
 * se simula y el evaluador RBAC {@code @autorizador} se sustituye por un doble para
 * evaluar realmente las expresiones {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = PruebaDisenoController.class,
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
        PruebaDisenoControllerTest.ConfiguracionPrueba.class})
class PruebaDisenoControllerTest {

    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID COTIZACION = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioPruebasDiseno servicioPruebasDiseno;

    @Autowired
    private Autorizador autorizador;

    private static PruebaDisenoDto pruebaDto(int numeroVersion, String estado) {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        Instant decidida = "pendiente".equals(estado) ? null : ahora;
        return new PruebaDisenoDto(ID, COTIZACION, numeroVersion, estado,
                "aprobada".equals(estado) ? "cliente" : null,
                "rechazada".equals(estado) ? "cliente" : null,
                decidida, 0L, ahora, ahora);
    }

    @BeforeEach
    void permitirGiroPorDefecto() {
        // El gating por Giro (tarea 8.4) se satisface por defecto para no romper el
        // camino feliz; cada test controla la autorizacion fina via {@code tiene(...)}.
        org.mockito.Mockito.lenient().when(autorizador.giroCorresponde(anyString())).thenReturn(true);
        // El gating por Plan (Req 25.4) tambien se satisface por defecto en el
        // camino feliz; los tests de 403 por modulo lo anulan explicitamente.
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    @Test
    void generar_devuelve201_conVersion1Pendiente() throws Exception {
        when(autorizador.tiene("prueba_diseno", "crear")).thenReturn(true);
        when(servicioPruebasDiseno.generar(eq(COTIZACION))).thenReturn(pruebaDto(1, "pendiente"));

        mockMvc.perform(post("/cotizaciones/{cotizacionId}/pruebas-diseno", COTIZACION)
                        .with(user("diseno")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.numeroVersion").value(1))
                .andExpect(jsonPath("$.estado").value("pendiente"));
    }

    @Test
    void aprobar_devuelve200_conAprobada() throws Exception {
        when(autorizador.tiene("prueba_diseno", "cambiar_estado")).thenReturn(true);
        when(servicioPruebasDiseno.aprobar(eq(ID))).thenReturn(pruebaDto(1, "aprobada"));

        mockMvc.perform(post("/pruebas-diseno/{id}/aprobar", ID).with(user("diseno")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("aprobada"))
                .andExpect(jsonPath("$.aprobadaPor").value("cliente"));
    }

    @Test
    void aprobar_propaga409_cuandoYaDecidida() throws Exception {
        when(autorizador.tiene("prueba_diseno", "cambiar_estado")).thenReturn(true);
        when(servicioPruebasDiseno.aprobar(eq(ID)))
                .thenThrow(new TransicionInvalidaException("La Prueba_Diseno ya esta decidida."));

        mockMvc.perform(post("/pruebas-diseno/{id}/aprobar", ID).with(user("diseno")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void rechazar_devuelve200_conRechazadaYNuevaVersion() throws Exception {
        when(autorizador.tiene("prueba_diseno", "cambiar_estado")).thenReturn(true);
        when(servicioPruebasDiseno.rechazar(eq(ID))).thenReturn(
                new ResultadoRechazoPruebaDiseno(pruebaDto(1, "rechazada"), pruebaDto(2, "pendiente")));

        mockMvc.perform(post("/pruebas-diseno/{id}/rechazar", ID).with(user("diseno")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rechazada.estado").value("rechazada"))
                .andExpect(jsonPath("$.rechazada.numeroVersion").value(1))
                .andExpect(jsonPath("$.nuevaVersion.estado").value("pendiente"))
                .andExpect(jsonPath("$.nuevaVersion.numeroVersion").value(2));
    }

    @Test
    void listar_devuelveFormaPaginada() throws Exception {
        when(autorizador.tiene("prueba_diseno", "listar")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<PruebaDisenoDto> pagina = new PageImpl<>(List.of(pruebaDto(1, "pendiente")), pageable, 1);
        when(servicioPruebasDiseno.listar(eq(COTIZACION), any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/cotizaciones/{cotizacionId}/pruebas-diseno", COTIZACION).with(user("diseno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void generar_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/cotizaciones/{cotizacionId}/pruebas-diseno", COTIZACION)
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
