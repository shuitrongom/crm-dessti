package com.dessti.crm.platform.monetizacion.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.monetizacion.application.FacturaRentaDto;
import com.dessti.crm.platform.monetizacion.application.ServicioFacturacionRenta;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link FacturaRentaController} centradas en el
 * nuevo endpoint {@code GET /facturas-renta/{id}/pdf}: reproducen el contrato
 * REST real sin BD ({@link ServicioFacturacionRenta} simulado) y evaluan la
 * expresion {@code @PreAuthorize('factura_renta','leer')} con un doble de
 * {@code @autorizador}. Replican el patron de {@code PlanControllerTest}.
 *
 * <p>Cubren: 200 con {@code application/pdf} para el super_admin con permiso,
 * 403 sin el permiso {@code factura_renta:leer} y 404 cuando la factura no existe.</p>
 */
@WebMvcTest(controllers = FacturaRentaController.class,
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
        FacturaRentaControllerPdfTest.ConfiguracionPrueba.class})
class FacturaRentaControllerPdfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioFacturacionRenta servicioFacturacion;

    @Autowired
    private Autorizador autorizador;

    private FacturaRentaDto dtoEjemplo(UUID id) {
        return new FacturaRentaDto(id, UUID.randomUUID(), LocalDate.of(2025, 6, 1), "MXN",
                new BigDecimal("800.00"), "emitida", Instant.parse("2025-06-15T12:00:00Z"),
                List.of());
    }

    @Test
    void descargarPdf_devuelve200PdfInline_paraSuperAdminConPermiso() throws Exception {
        UUID id = UUID.randomUUID();
        when(autorizador.tiene("factura_renta", "leer")).thenReturn(true);
        when(servicioFacturacion.consultar(id)).thenReturn(dtoEjemplo(id));
        when(servicioFacturacion.generarPdf(id))
                .thenReturn(new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4'});

        mockMvc.perform(get("/facturas-renta/{id}/pdf", id).with(user("super_admin")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("inline")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".pdf")));
    }

    @Test
    void descargarPdf_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/facturas-renta/{id}/pdf", UUID.randomUUID())
                        .with(user("rol_empresa")))
                .andExpect(status().isForbidden());
    }

    @Test
    void descargarPdf_devuelve404_cuandoLaFacturaNoExiste() throws Exception {
        UUID id = UUID.randomUUID();
        when(autorizador.tiene("factura_renta", "leer")).thenReturn(true);
        when(servicioFacturacion.consultar(any()))
                .thenThrow(new RecursoNoEncontradoException("No se encontro la factura de renta solicitada."));

        mockMvc.perform(get("/facturas-renta/{id}/pdf", id).with(user("super_admin")))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
