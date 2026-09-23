package com.dessti.crm.platform.arranque;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de arranque/smoke (Tarea 49.3) que <strong>arranca el contexto completo
 * de Spring</strong> contra una base de datos PostgreSQL real (Testcontainers) y
 * verifica los puntos transversales de disponibilidad y documentacion:
 *
 * <ol>
 *   <li><b>Health check (Req 51.4):</b> {@code /actuator/health} responde 200 y
 *       reporta estado {@code UP}.</li>
 *   <li><b>OpenAPI (Req 13):</b> {@code /v3/api-docs} publica una especificacion
 *       OpenAPI navegable.</li>
 * </ol>
 *
 * <p>Al arrancar el contexto con {@code ddl-auto=validate} y Flyway sobre una BD
 * limpia, esta prueba tambien valida <em>implicitamente</em> que las entidades
 * JPA (incluida la de la bitacora de respaldo de la tarea 49.2) casan con el
 * esquema de las migraciones.</p>
 *
 * <p><strong>Secretos y llaves de PRUEBA:</strong> el arranque exige los
 * secretos del Req 11 y una {@code Llave_Cifrado} activa (Req 67). El validador
 * de secretos ({@code SecretosEnvironmentPostProcessor}) es un
 * {@code EnvironmentPostProcessor} que corre <strong>muy temprano</strong>
 * (durante la preparacion del entorno), <em>antes</em> de que se apliquen los
 * valores de {@link DynamicPropertySource} y las {@code properties} inline de
 * {@link SpringBootTest}. Por eso los secretos de PRUEBA (jamas reales) con
 * valor CONSTANTE se inyectan como <strong>propiedades de sistema</strong> en un
 * bloque {@code static} que se ejecuta al cargar la clase (antes de que Spring
 * arranque): las propiedades de sistema son un {@code PropertySource} estandar
 * visible para los {@code EnvironmentPostProcessor}. Asi el validador temprano
 * ve {@code spring.datasource.username}/{@code password} y
 * {@code crm.secretos.jwt-signing-key}, ademas de los nombres de variable que
 * {@code application.yml} interpola ({@code ${JWT_SIGNING_KEY}},
 * {@code ${CRM_ENC_KEY_ACTIVE}}, {@code ${CRM_ENC_KEY_V1}}, {@code ${DB_USER}},
 * {@code ${DB_PASSWORD}}). Las credenciales del contenedor coinciden con los
 * literales fijos ({@code crm_test}/{@code crm_test_pwd}). Solo la URL con
 * puerto aleatorio (conocida tras arrancar el contenedor) queda en
 * {@link DynamicPropertySource}. La capacidad de respaldo se deja
 * <em>deshabilitada</em> ({@code crm.respaldo.habilitado=false}) para que el
 * arranque no invoque procesos externos ni dispare respaldos programados.</p>
 *
 * <p>Las propiedades de sistema son globales del JVM y persisten entre clases de
 * prueba del mismo <em>fork</em>; por eso {@link #limpiarSecretos()} las libera
 * en {@code @AfterAll} para no filtrarlas a otras pruebas.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("Tarea 49.3 - Smoke: contexto arranca, /actuator/health UP y OpenAPI publicado (Req 51.4, 13)")
class ArranqueContextoSmokeIT {

    static {
        // Se ejecuta al CARGAR la clase, antes de que Spring arranque, para que el
        // EnvironmentPostProcessor de validacion de secretos (Req 11, 67), que corre
        // durante la preparacion del entorno, encuentre los secretos requeridos.
        // No referencia el contenedor: usa los literales fijos de credenciales.
        System.setProperty("spring.datasource.username", "crm_test");
        System.setProperty("spring.datasource.password", "crm_test_pwd");
        System.setProperty("crm.secretos.jwt-signing-key",
                "clave-de-firma-jwt-solo-para-pruebas-no-usar-en-produccion-1234567890");
        System.setProperty("crm.cifrado.activa", "v1");
        System.setProperty("crm.cifrado.llaves.v1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        // Nombres de variable que application.yml interpola en esa misma fase temprana.
        System.setProperty("DB_USER", "crm_test");
        System.setProperty("DB_PASSWORD", "crm_test_pwd");
        System.setProperty("JWT_SIGNING_KEY",
                "clave-de-firma-jwt-solo-para-pruebas-no-usar-en-produccion-1234567890");
        System.setProperty("CRM_ENC_KEY_ACTIVE", "v1");
        System.setProperty("CRM_ENC_KEY_V1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        System.setProperty("crm.respaldo.habilitado", "false");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("crm")
                    .withUsername("crm_test")
                    .withPassword("crm_test_pwd");

    @AfterAll
    static void limpiarSecretos() {
        // Las propiedades de sistema son globales del JVM: liberarlas evita filtrar
        // los secretos de PRUEBA a otras clases del mismo fork de Failsafe.
        System.clearProperty("spring.datasource.username");
        System.clearProperty("spring.datasource.password");
        System.clearProperty("crm.secretos.jwt-signing-key");
        System.clearProperty("crm.cifrado.activa");
        System.clearProperty("crm.cifrado.llaves.v1");
        System.clearProperty("DB_USER");
        System.clearProperty("DB_PASSWORD");
        System.clearProperty("JWT_SIGNING_KEY");
        System.clearProperty("CRM_ENC_KEY_ACTIVE");
        System.clearProperty("CRM_ENC_KEY_V1");
        System.clearProperty("crm.respaldo.habilitado");
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // La URL con puerto aleatorio solo se conoce tras arrancar el contenedor.
        // El validador temprano de secretos no revisa la URL (solo usuario/clave y
        // la llave JWT, ya cubiertos por propiedades de sistema); el DataSource se
        // crea tarde durante el refresh, por lo que la URL puede quedar aqui. El
        // usuario/clave del contenedor coinciden con los literales fijos y se
        // repiten aqui de forma inofensiva para alimentar tambien el bean DataSource.
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("DB_URL", POSTGRES::getJdbcUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("(a) /actuator/health responde 200 y estado UP (Req 51.4)")
    void health_disponibleYUp() throws Exception {
        // La ruta es relativa al context-path /api/v1 (configurado en application.yml).
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("(b) /v3/api-docs publica la especificacion OpenAPI (Req 13)")
    void openApi_publicado() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths").exists());
    }
}
