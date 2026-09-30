package com.dessti.crm.comercial.producto.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
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
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort.ConsultaSugerenciaPrecio;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link PrecioSugeridoController} (Req 59.4,
 * 59.9). Reproduce el contrato REST real sin BD: el puerto de dominio
 * {@link SugerenciaPrecioPort} se simula (la regla de seleccion se prueba en su
 * propio test unitario) y el evaluador RBAC {@code @autorizador} se sustituye por
 * un doble para evaluar de verdad las expresiones {@code @PreAuthorize}. El
 * {@link Clock} se fija a una fecha conocida para verificar el valor por defecto.
 */
@WebMvcTest(controllers = PrecioSugeridoController.class,
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
        PrecioSugeridoControllerTest.ConfiguracionPrueba.class})
class PrecioSugeridoControllerTest {

    private static final UUID PRODUCTO_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    /** Fecha "de hoy" que el Clock fijo devuelve (ver ConfiguracionPrueba). */
    private static final LocalDate HOY = LocalDate.parse("2026-03-15");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SugerenciaPrecioPort sugerenciaPrecio;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permitirModuloPorDefecto() {
        org.mockito.Mockito.lenient().when(autorizador.moduloHabilitado(anyString())).thenReturn(true);
    }

    @Test
    void sugerir_devuelve200_conPrecioDisponible_yFechaPorDefecto() throws Exception {
        when(autorizador.tiene("lista_precios", "leer")).thenReturn(true);
        when(sugerenciaPrecio.sugerirPrecioUnitario(any(ConsultaSugerenciaPrecio.class)))
                .thenReturn(Optional.of(new BigDecimal("1234.56")));

        mockMvc.perform(get("/productos/{id}/precio-sugerido", PRODUCTO_ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value(PRODUCTO_ID.toString()))
                .andExpect(jsonPath("$.precioSugerido").value(1234.56))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.fechaReferencia").value(HOY.toString()))
                .andExpect(jsonPath("$.segmentoCliente").doesNotExist());

        // Sin fecha explicita se usa la de hoy segun el Clock; sin segmento se pasa null.
        ArgumentCaptor<ConsultaSugerenciaPrecio> captor =
                ArgumentCaptor.forClass(ConsultaSugerenciaPrecio.class);
        verify(sugerenciaPrecio).sugerirPrecioUnitario(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().productoId()).isEqualTo(PRODUCTO_ID);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().fecha()).isEqualTo(HOY);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().segmentoCliente()).isNull();
    }

    @Test
    void sugerir_devuelve200_conDisponibleFalse_cuandoNoHaySugerencia() throws Exception {
        when(autorizador.tiene("lista_precios", "leer")).thenReturn(true);
        when(sugerenciaPrecio.sugerirPrecioUnitario(any(ConsultaSugerenciaPrecio.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/productos/{id}/precio-sugerido", PRODUCTO_ID).with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value(PRODUCTO_ID.toString()))
                .andExpect(jsonPath("$.precioSugerido").doesNotExist())
                .andExpect(jsonPath("$.disponible").value(false));
    }

    @Test
    void sugerir_usaFechaYSegmentoIndicados() throws Exception {
        when(autorizador.tiene("lista_precios", "leer")).thenReturn(true);
        when(sugerenciaPrecio.sugerirPrecioUnitario(any(ConsultaSugerenciaPrecio.class)))
                .thenReturn(Optional.of(new BigDecimal("500.00")));

        mockMvc.perform(get("/productos/{id}/precio-sugerido", PRODUCTO_ID)
                        .param("fecha", "2026-01-31")
                        .param("segmento", "mayoreo")
                        .with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaReferencia").value("2026-01-31"))
                .andExpect(jsonPath("$.segmentoCliente").value("mayoreo"));

        ArgumentCaptor<ConsultaSugerenciaPrecio> captor =
                ArgumentCaptor.forClass(ConsultaSugerenciaPrecio.class);
        verify(sugerenciaPrecio).sugerirPrecioUnitario(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().fecha())
                .isEqualTo(LocalDate.parse("2026-01-31"));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().segmentoCliente())
                .isEqualTo("mayoreo");
    }

    @Test
    void sugerir_normalizaSegmentoEnBlanco_aNull() throws Exception {
        when(autorizador.tiene("lista_precios", "leer")).thenReturn(true);
        when(sugerenciaPrecio.sugerirPrecioUnitario(any(ConsultaSugerenciaPrecio.class)))
                .thenReturn(Optional.of(new BigDecimal("10.00")));

        mockMvc.perform(get("/productos/{id}/precio-sugerido", PRODUCTO_ID)
                        .param("segmento", "   ")
                        .with(user("ventas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentoCliente").doesNotExist());

        ArgumentCaptor<ConsultaSugerenciaPrecio> captor =
                ArgumentCaptor.forClass(ConsultaSugerenciaPrecio.class);
        verify(sugerenciaPrecio).sugerirPrecioUnitario(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().segmentoCliente()).isNull();
    }

    @Test
    void sugerir_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/productos/{id}/precio-sugerido", PRODUCTO_ID).with(user("sin_permiso")))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }

        /** Clock fijo a HOY (2026-03-15, UTC) para verificar la fecha por defecto. */
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
