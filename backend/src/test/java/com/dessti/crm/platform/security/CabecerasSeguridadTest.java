package com.dessti.crm.platform.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.config.SecretosProperties;

/**
 * Pruebas de la tarea 12 (Req 9.3): verifican que la cadena de Spring Security
 * definida en {@link SecurityConfig} emite las cabeceras de seguridad del
 * navegador en cada respuesta.
 *
 * <p>Se usa una <b>slice</b> {@link WebMvcTest} (sin arrancar la aplicacion
 * completa ni la base de datos) que importa {@link SecurityConfig} y sus
 * dependencias de propiedades, junto a un controlador trivial de prueba. La
 * peticion se marca como segura ({@code secure(true)}) porque HSTS solo se
 * emite sobre HTTPS; en produccion ese esquema se reconstruye desde
 * {@code X-Forwarded-Proto} enviado por IIS.</p>
 */
@WebMvcTest(controllers = CabecerasSeguridadTest.PingController.class,
        properties = {
                // Valores dummy para los secretos requeridos (Req 11): el slice
                // no usa BD ni firma real. Se fijan tanto las variables de entorno
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
@Import({SecurityConfig.class, CabecerasSeguridadTest.PropiedadesPrueba.class})
class CabecerasSeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void respuesta_incluyeContentSecurityPolicyRestrictiva() throws Exception {
        mockMvc.perform(get("/ping").secure(true).with(user("prueba")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        SecurityConfig.CONTENT_SECURITY_POLICY));
    }

    @Test
    void respuesta_incluyeStrictTransportSecurityConSubdominios() throws Exception {
        // Formato de la cabecera: max-age=<segundos> ; includeSubDomains.
        mockMvc.perform(get("/ping").secure(true).with(user("prueba")))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security",
                        "max-age=" + SecurityConfig.HSTS_MAX_AGE_SEGUNDOS + " ; includeSubDomains"));
    }

    @Test
    void respuesta_incluyeXFrameOptionsDeny() throws Exception {
        mockMvc.perform(get("/ping").secure(true).with(user("prueba")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void respuesta_incluyeReferrerPolicyNoReferrer() throws Exception {
        mockMvc.perform(get("/ping").secure(true).with(user("prueba")))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", SecurityConfig.REFERRER_POLICY));
    }

    @Test
    void respuesta_incluyeXContentTypeOptionsNosniff() throws Exception {
        mockMvc.perform(get("/ping").secure(true).with(user("prueba")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    /** Controlador trivial protegido: sirve para provocar una respuesta 200. */
    @RestController
    static class PingController {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }
    }

    /**
     * Beans de propiedades que {@link SecurityConfig} necesita para construir
     * sus {@code @Bean} internos (reloj, servicio de tokens y filtro JWT) sin
     * exponer ningun secreto real.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class PropiedadesPrueba {

        @Bean
        PingController pingController() {
            return new PingController();
        }

        @Bean
        SecretosProperties secretosProperties() {
            return new SecretosProperties(
                    "test-db-user",
                    "test-db-password",
                    "clave-de-firma-jwt-solo-para-pruebas-0123456789");
        }

        // JwtProperties lo provee SecurityConfig via @EnableConfigurationProperties;
        // sus valores por defecto (15 min / 7 dias) bastan para esta prueba.
    }
}
