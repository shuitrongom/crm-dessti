package com.dessti.crm.estrategia.adapter.in.rest;

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

import com.dessti.crm.estrategia.application.ObjetivoEstrategicoDto;
import com.dessti.crm.estrategia.application.ServicioEstrategia;
import com.dessti.crm.platform.security.MethodSecurityConfig;
import com.dessti.crm.platform.security.rbac.Autorizador;
import com.dessti.crm.platform.web.ManejadorGlobalErrores;

/**
 * Prueba de rebanada (slice) del <strong>gating por Plan</strong> (Req 25.4) en el
 * {@link EstrategiaController} del modulo {@code estrategia}.
 *
 * <p>Un Usuario CON el permiso RBAC {@code objetivo_estrategico:listar} pero cuya
 * Empresa NO contrata el modulo {@code estrategia} recibe 403; con el modulo
 * contratado y el permiso, responde 200.</p>
 */
@WebMvcTest(controllers = EstrategiaController.class,
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
        EstrategiaControllerModuloTest.ConfiguracionPrueba.class})
class EstrategiaControllerModuloTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioEstrategia servicioEstrategia;

    @Autowired
    private Autorizador autorizador;

    @BeforeEach
    void permisoPresentePorDefecto() {
        org.mockito.Mockito.lenient().when(autorizador.tiene(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void listar_devuelve403_cuandoModuloEstrategiaNoContratado() throws Exception {
        when(autorizador.moduloHabilitado("estrategia")).thenReturn(false);

        mockMvc.perform(get("/estrategia/objetivos").with(user("gerente")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_devuelve200_cuandoModuloContratadoYconPermiso() throws Exception {
        when(autorizador.moduloHabilitado("estrategia")).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<ObjetivoEstrategicoDto> vacia = new PageImpl<>(java.util.List.of(), pageable, 0);
        when(servicioEstrategia.listarObjetivos(any(), any(), any(Pageable.class))).thenReturn(vacia);

        mockMvc.perform(get("/estrategia/objetivos").with(user("gerente")))
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
