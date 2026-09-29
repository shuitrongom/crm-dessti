package com.dessti.crm.operacion.proyecto.application.evidencia;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita el enlace tipado de {@link EvidenciaStorageProperties}
 * ({@code crm.evidencias.storage.*}), siguiendo el mismo patron que el resto de
 * la plataforma (p. ej. {@code MonetizacionConfig}). El adaptador de
 * almacenamiento ({@code EvidenciaStorageFilesystemAdapter}) inyecta estas
 * propiedades para resolver el directorio base y los limites de tamano/MIME.
 */
@Configuration
@EnableConfigurationProperties(EvidenciaStorageProperties.class)
public class EvidenciaStorageConfig {
}
