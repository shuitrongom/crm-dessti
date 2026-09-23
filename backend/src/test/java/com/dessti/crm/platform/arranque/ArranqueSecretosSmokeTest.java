package com.dessti.crm.platform.arranque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import com.dessti.crm.platform.config.SecretoFaltanteException;
import com.dessti.crm.platform.config.SecretosEnvironmentPostProcessor;

/**
 * Prueba de arranque/smoke (Tarea 49.3) del <strong>fail-fast por secreto
 * requerido ausente</strong> ejercitando el hook real de arranque
 * {@link SecretosEnvironmentPostProcessor} (Req 11.2, 11.3).
 *
 * <p>Mientras que {@code SecretosValidadorTest} prueba la logica de validacion,
 * esta prueba verifica el <em>punto de integracion</em> que Spring Boot invoca
 * al preparar el entorno, garantizando que el arranque se detiene si falta un
 * secreto y que el mensaje no expone su valor. No arranca el contexto de Spring
 * (solo el post-procesador de entorno), por lo que es determinista y no requiere
 * base de datos.</p>
 */
@DisplayName("Tarea 49.3 - Smoke: el arranque se aborta si falta un secreto requerido (Req 11.2)")
class ArranqueSecretosSmokeTest {

    private final SecretosEnvironmentPostProcessor postProcessor = new SecretosEnvironmentPostProcessor();

    private MockEnvironment entornoCompleto() {
        return new MockEnvironment()
                .withProperty("spring.datasource.username", "usuario_bd")
                .withProperty("spring.datasource.password", "clave_bd_prueba")
                .withProperty("crm.secretos.jwt-signing-key", "clave_firma_jwt_prueba");
    }

    @Test
    @DisplayName("El post-procesador aborta el arranque cuando falta la clave de firma JWT")
    void postProcesador_faltaSecreto_abortaArranque() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", "usuario_bd")
                .withProperty("spring.datasource.password", "clave_bd_prueba");
        // JWT_SIGNING_KEY ausente a proposito.

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(env, new SpringApplication()))
                .isInstanceOf(SecretoFaltanteException.class)
                .hasMessageContaining("JWT_SIGNING_KEY")
                // El mensaje NUNCA expone el valor de un secreto presente (Req 11.3).
                .extracting(Throwable::getMessage)
                .satisfies(mensaje -> assertThat(mensaje).doesNotContain("clave_bd_prueba"));
    }

    @Test
    @DisplayName("El post-procesador permite el arranque cuando todos los secretos estan presentes")
    void postProcesador_conTodosLosSecretos_noAborta() {
        MockEnvironment env = entornoCompleto();

        assertThatCode(() -> postProcessor.postProcessEnvironment(env, new SpringApplication()))
                .doesNotThrowAnyException();
    }
}
