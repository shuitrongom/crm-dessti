package com.dessti.crm.platform.empresas.offboarding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del modulo de offboarding del tenant (Req 69, tarea 14.4).
 *
 * <p>Habilita el enlace tipado de {@link OffboardingProperties} (Periodo_Gracia
 * configurable, Req 69.2), siguiendo el mismo patron que el resto de la
 * plataforma (por ejemplo {@code AuditoriaConfig}). El {@link ServicioOffboarding}
 * y los beans {@link RecursoTenantOffboarding} se detectan por component-scan.</p>
 */
@Configuration
@EnableConfigurationProperties(OffboardingProperties.class)
public class OffboardingConfig {
}
