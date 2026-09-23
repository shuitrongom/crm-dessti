package com.dessti.crm.platform.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades configurables del limitador de tasa por IP (Req 2.4). Los valores
 * por defecto implementan el requisito: 100 peticiones por minuto por IP.
 *
 * <p>Prefijo de configuracion: {@code crm.seguridad.rate-limit}.</p>
 *
 * @param maxPeticiones peticiones permitidas por ventana (por defecto 100).
 * @param ventanaMillis duracion de la ventana en milisegundos (por defecto
 *                      60000 = 1 minuto).
 */
@ConfigurationProperties(prefix = "crm.seguridad.rate-limit")
public record LimiteTasaProperties(
        Integer maxPeticiones,
        Long ventanaMillis) {

    /** Maximo de peticiones por minuto por IP exigido por el Req 2.4. */
    public static final int MAX_PETICIONES_DEFECTO = 100;

    /** Ventana de 1 minuto en milisegundos (Req 2.4). */
    public static final long VENTANA_MILLIS_DEFECTO = 60_000L;

    public LimiteTasaProperties {
        if (maxPeticiones == null) {
            maxPeticiones = MAX_PETICIONES_DEFECTO;
        }
        if (ventanaMillis == null) {
            ventanaMillis = VENTANA_MILLIS_DEFECTO;
        }
    }

    /** @return duracion de la ventana en segundos, para la cabecera Retry-After. */
    public long ventanaSegundos() {
        return Math.max(1, ventanaMillis / 1000L);
    }
}
