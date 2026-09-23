package com.dessti.crm.compras.factura.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del submodulo de Facturas de Proveedor (Req 33, tarea 27.2).
 *
 * <p>Habilita el enlace tipado de {@link ConciliacionProperties} (tolerancia de
 * precio configurable de la Conciliacion_Tres_Vias, Req 33.3), siguiendo el mismo
 * patron que el resto de la plataforma (por ejemplo {@code OffboardingConfig} y
 * {@code AuditoriaConfig}). El {@code ServicioFacturasProveedor} recibe las
 * propiedades por inyeccion.</p>
 */
@Configuration
@EnableConfigurationProperties(ConciliacionProperties.class)
public class ConciliacionConfig {
}
