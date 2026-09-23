package com.dessti.crm.platform.monetizacion.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del modulo de monetizacion de plataforma (Fase A de precios).
 *
 * <p>Habilita el enlace tipado de {@link MonetizacionProperties} (moneda principal
 * configurable) y de {@link EmisorProperties} (datos del emisor Dess-TI que se
 * imprimen en el comprobante de renta), siguiendo el mismo patron que el resto de
 * la plataforma (por ejemplo {@code OffboardingConfig}). El motor de monetizacion
 * existente (servicios y adaptadores) se detecta por component-scan.</p>
 */
@Configuration
@EnableConfigurationProperties({MonetizacionProperties.class, EmisorProperties.class})
public class MonetizacionConfig {
}
