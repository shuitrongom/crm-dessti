package com.dessti.crm.platform.empresas.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.security.rbac.ModulosHabilitadosPort;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Pruebas de rebanada (slice) del {@link ModulosEmpresaController}: reproducen el
 * contrato REST real de {@code GET /empresa/modulos} sin BD
 * ({@link ModulosHabilitadosPort} simulado). Replican el patron de
 * {@code PerfilControllerTest}/{@code EmpresaControllerCuentaTest}.
 *
 * <p>Cubren: tenant que contrata {@code ["estrategia","comercial"]} &rarr; devuelve
 * exactamente esas claves; tenant sin modulos &rarr; arreglo vacio; sin
 * autenticacion &rarr; 401; y Usuario autenticado SIN tenant (p. ej.
 * {@code super_admin} de plataforma) &rarr; 403.</p>
 *
 * <p>El {@link TenantContext} es un {@code ThreadLocal} que en produccion fija el
 * {@code TenantResolutionFilter} (fuera de esta rebanada). Aqui se establece a mano
 * antes de la peticion (MockMvc corre en el mismo hilo) y se limpia tras cada
 * prueba para no filtrar el tenant entre casos.</p>
 */
@WebMvcTest(controllers = ModulosEmpresaController.class,
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
        ModulosEmpresaControllerTest.ConfiguracionPrueba.class})
class ModulosEmpresaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModulosHabilitadosPort modulosHabilitados;

    @AfterEach
    void limpiarContexto() {
        // El TenantContext es thread-local: se limpia tras cada caso (Req 23).
        TenantContext.clear();
    }

    @Test
    void getModulos_tenantConDosModulos_devuelveExactamenteEsasClaves() throws Exception {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(modulosHabilitados.modulosHabilitadosDe(eq(tenant)))
                .thenReturn(List.of("estrategia", "comercial"));

        mockMvc.perform(get("/empresa/modulos").with(user("ventas@empresa.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modulos.length()").value(2))
                .andExpect(jsonPath("$.modulos[0]").value("estrategia"))
                .andExpect(jsonPath("$.modulos[1]").value("comercial"));
    }

    @Test
    void getModulos_tenantSinModulos_devuelveArregloVacio() throws Exception {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(modulosHabilitados.modulosHabilitadosDe(eq(tenant))).thenReturn(List.of());

        mockMvc.perform(get("/empresa/modulos").with(user("admin@empresa.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modulos.length()").value(0));
    }

    @Test
    void getModulos_sinAutenticacion_devuelve401() throws Exception {
        mockMvc.perform(get("/empresa/modulos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getModulos_autenticadoSinTenant_devuelve403() throws Exception {
        // Un Usuario de plataforma (super_admin) supera isAuthenticated() pero no
        // tiene tenant en contexto: el gating de modulos no le aplica -> 403.
        mockMvc.perform(get("/empresa/modulos").with(user("super_admin")))
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
