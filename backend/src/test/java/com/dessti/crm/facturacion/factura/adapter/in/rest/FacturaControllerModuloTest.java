package com.dessti.crm.facturacion.factura.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.dessti.crm.facturacion.factura.application.FacturaDto;
import com.dessti.crm.facturacion.factura.application.ServicioFacturas;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Prueba de rebanada (slice) del <strong>gating por Plan</strong> (Req 25.4) en el
 * {@link FacturaController} del modulo {@code facturacion}.
 *
 * <p>Verifica la defensa en profundidad: un Usuario CON el permiso RBAC
 * {@code factura:listar} pero cuya Empresa NO contrata el modulo
 * {@code facturacion} recibe 403; cuando el modulo esta contratado y tiene el
 * permiso, el endpoint responde 200. El {@code @autorizador} se sustituye por un
 * doble para evaluar realmente las expresiones {@code @PreAuthorize}.</p>
 */
@WebMvcTest(controllers = FacturaController.class,
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
        FacturaControllerModuloTest.ConfiguracionPrueba.class})
class FacturaControllerModuloTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioFacturas servicioFacturas;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permisoPresentePorDefecto() {
        // El permiso RBAC atomico se concede en todos los casos; lo que se prueba
        // aqui es exclusivamente el gating por modulo (Req 25.4).
        org.mockito.Mockito.lenient().when(autorizador.tiene(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void listar_devuelve403_cuandoModuloFacturacionNoContratado() throws Exception {
        // La Empresa NO contrata 'facturacion': 403 pese al permiso RBAC.
        when(autorizador.moduloHabilitado("facturacion")).thenReturn(false);

        mockMvc.perform(get("/facturacion/facturas").with(user("contador")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_devuelve200_cuandoModuloContratadoYconPermiso() throws Exception {
        // La Empresa SI contrata 'facturacion' y el Usuario tiene el permiso: pasa.
        when(autorizador.moduloHabilitado("facturacion")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<FacturaDto> vacia = new PageImpl<>(java.util.List.of(), pageable, 0);
        when(servicioFacturas.listar(any(), any(), any(Pageable.class))).thenReturn(vacia);

        mockMvc.perform(get("/facturacion/facturas").with(user("contador")))
                .andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {

        @Bean("autorizador")
        Autorizador autorizador() {
            return org.mockito.Mockito.mock(Autorizador.class);
        }
    }
}
