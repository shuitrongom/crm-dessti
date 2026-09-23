package com.dessti.crm.platform.empresas.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.empresas.EmpresaCreadaDto;
import com.dessti.crm.platform.empresas.EmpresaDto;
import com.dessti.crm.platform.empresas.EstadoEmpresa;
import com.dessti.crm.platform.empresas.ServicioEmpresas;
import com.dessti.crm.platform.empresas.ServicioSuscripciones;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link EmpresaController} centradas en el
 * <strong>alta con instrumento excluyente</strong> del rediseno
 * {@code plan-vs-suscripcion-contratacion} ({@code POST /empresas}): el Contrato
 * inicial se asocia a EXACTAMENTE uno de {@code planId} o
 * {@code paqueteSuscripcionId} (XOR, Req 4). Reproducen el contrato REST real sin
 * BD ({@link ServicioEmpresas} y {@link ServicioSuscripciones} simulados) y
 * evaluan realmente la expresion {@code @PreAuthorize('empresa','crear')} con un
 * doble de {@code @autorizador}. Replican el patron de {@code PlanControllerTest}
 * y {@code GiroControllerTest}.
 *
 * <p>Cubren (Req 4.3, 4.4, 10): 201 al indicar solo {@code planId}; 201 al indicar
 * solo {@code paqueteSuscripcionId}; 422 cuando se indican AMBOS (Req 4.4) o
 * NINGUNO (Req 4.3), mapeando la {@link ReglaNegocioException} del servicio; y 403
 * sin el permiso {@code empresa:crear}.</p>
 */
@WebMvcTest(controllers = EmpresaController.class,
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
        EmpresaControllerAltaExcluyenteTest.ConfiguracionPrueba.class})
class EmpresaControllerAltaExcluyenteTest {

    private static final String GIRO = "11111111-1111-1111-1111-111111111111";
    private static final String PLAN = "22222222-2222-2222-2222-222222222222";
    private static final String PAQUETE = "33333333-3333-3333-3333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioEmpresas servicioEmpresas;

    @MockBean
    private ServicioSuscripciones servicioSuscripciones;

    @Autowired
    private Autorizador autorizador;

    /** Cuerpo del alta con un solo instrumento; {@code instrumentoJson} inyecta planId o paqueteSuscripcionId. */
    private static String cuerpoAlta(String instrumentoJson) {
        return """
                {
                  "nombre": "ACME S.A. de C.V.",
                  "rfc": "ACM120101AB1",
                  "giroId": "%s",
                  %s
                  "adminIdentificador": "admin@acme.test",
                  "emailContacto": "contacto@acme.test"
                }
                """.formatted(GIRO, instrumentoJson);
    }

    private static EmpresaCreadaDto empresaCreada() {
        EmpresaDto empresa = new EmpresaDto(
                UUID.randomUUID(), "ACME S.A. de C.V.", "ACM120101AB1", UUID.randomUUID(),
                EstadoEmpresa.ACTIVA, null, null, null, "contacto@acme.test", null, null,
                new EmpresaDto.DireccionDto(null, null, null, null, null),
                null, null, null, Instant.now(), Instant.now(), null);
        return new EmpresaCreadaDto(empresa, UUID.randomUUID(), "admin@acme.test", "TempPass123");
    }

    @Test
    void crear_devuelve201_conSoloPlanId() throws Exception {
        when(autorizador.tiene("empresa", "crear")).thenReturn(true);
        when(servicioEmpresas.crearEmpresa(any())).thenReturn(empresaCreada());

        mockMvc.perform(post("/empresas").with(user("super_admin")).with(csrf())
                        .contentType("application/json")
                        .content(cuerpoAlta("\"planId\": \"" + PLAN + "\",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresa.nombre").value("ACME S.A. de C.V."))
                .andExpect(jsonPath("$.adminPasswordTemporal").value("TempPass123"));
    }

    @Test
    void crear_devuelve201_conSoloPaqueteSuscripcionId() throws Exception {
        when(autorizador.tiene("empresa", "crear")).thenReturn(true);
        when(servicioEmpresas.crearEmpresa(any())).thenReturn(empresaCreada());

        mockMvc.perform(post("/empresas").with(user("super_admin")).with(csrf())
                        .contentType("application/json")
                        .content(cuerpoAlta("\"paqueteSuscripcionId\": \"" + PAQUETE + "\",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresa.nombre").value("ACME S.A. de C.V."));
    }

    @Test
    void crear_devuelve422_cuandoSeIndicanAmbosInstrumentos() throws Exception {
        when(autorizador.tiene("empresa", "crear")).thenReturn(true);
        when(servicioEmpresas.crearEmpresa(any())).thenThrow(new ReglaNegocioException(
                "Debe indicarse exactamente un instrumento de contratacion (Plan o Suscripcion), no ambos."));

        mockMvc.perform(post("/empresas").with(user("super_admin")).with(csrf())
                        .contentType("application/json")
                        .content(cuerpoAlta("\"planId\": \"" + PLAN + "\", \"paqueteSuscripcionId\": \""
                                + PAQUETE + "\",")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crear_devuelve422_cuandoNoSeIndicaNingunInstrumento() throws Exception {
        when(autorizador.tiene("empresa", "crear")).thenReturn(true);
        when(servicioEmpresas.crearEmpresa(any())).thenThrow(new ReglaNegocioException(
                "Debe indicarse exactamente un instrumento de contratacion (Plan o Suscripcion)."));

        mockMvc.perform(post("/empresas").with(user("super_admin")).with(csrf())
                        .contentType("application/json")
                        .content(cuerpoAlta("")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crear_devuelve403_cuandoFaltaPermiso() throws Exception {
        when(autorizador.tiene(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/empresas").with(user("rol_empresa")).with(csrf())
                        .contentType("application/json")
                        .content(cuerpoAlta("\"planId\": \"" + PLAN + "\",")))
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
