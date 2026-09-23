package com.dessti.crm.platform.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Post-procesador de entorno que valida los secretos requeridos <b>al inicio del
 * arranque</b>, antes de crear el contexto de aplicación y sus beans
 * (incluida la fuente de datos), garantizando un fallo temprano (fail-fast)
 * conforme al Requisito 11.2.
 *
 * <p>Delega la lógica en {@link SecretosValidador}. Si falta un secreto
 * requerido, se lanza {@link SecretoFaltanteException} y el arranque se detiene,
 * registrando el <b>nombre</b> del secreto ausente sin exponer su valor
 * (Req 11.2, 11.3).</p>
 *
 * <p>Se registra en {@code META-INF/spring.factories} para que Spring Boot lo
 * ejecute durante la preparación del entorno.</p>
 */
public class SecretosEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        SecretosValidador.validar(environment);
    }
}
