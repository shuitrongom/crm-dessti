package com.dessti.crm.platform.empresas;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del módulo de contratación de instrumentos (Plan/Suscripción).
 *
 * <p>Habilita el enlace tipado de {@link ContratacionProperties} (umbral de aviso
 * de vencimiento, Req 7.2/7.3), siguiendo el mismo patrón que el resto de la
 * plataforma (por ejemplo {@code OffboardingConfig}).</p>
 */
@Configuration
@EnableConfigurationProperties(ContratacionProperties.class)
public class ContratacionConfig {
}
