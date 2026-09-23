package com.dessti.crm.platform.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración que habilita el enlace tipado de los secretos del Sistema
 * (Requisito 11).
 *
 * <p>Registra {@link SecretosProperties} como bean de propiedades, resuelto
 * desde el entorno (variables de entorno o almacén externo). La verificación de
 * <i>presencia</i> de los secretos al arranque la realiza
 * {@link SecretosEnvironmentPostProcessor} de forma temprana (fail-fast).</p>
 */
@Configuration
@EnableConfigurationProperties(SecretosProperties.class)
public class SecretosConfig {
}
