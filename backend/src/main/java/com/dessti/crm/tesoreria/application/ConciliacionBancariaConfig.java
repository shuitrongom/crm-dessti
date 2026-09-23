package com.dessti.crm.tesoreria.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de la conciliacion bancaria (Req 43.3). Registra
 * {@link ConciliacionBancariaProperties} como bean de propiedades, resuelto desde
 * {@code crm.tesoreria.conciliacion.*}, siguiendo el mismo patron que
 * {@code ConciliacionConfig} del modulo de compras.
 */
@Configuration
@EnableConfigurationProperties(ConciliacionBancariaProperties.class)
public class ConciliacionBancariaConfig {
}
