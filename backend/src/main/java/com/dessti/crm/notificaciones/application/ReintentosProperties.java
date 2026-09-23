package com.dessti.crm.notificaciones.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Politica de reintentos <strong>configurable</strong> del envio de Notificaciones
 * (Req 46.3). Cuando un intento de envio falla, la {@code ServicioNotificaciones}
 * reintenta el despacho hasta {@link #maxIntentos()} veces, registrando el resultado
 * de cada intento. Sigue el patron de {@code PacProperties}: record anotado con
 * {@link ConfigurationProperties} y registrado via {@code @EnableConfigurationProperties}.
 *
 * <p>Los valores provienen de {@code crm.notificaciones.reintentos.*} (variables de
 * entorno con valores por defecto no sensibles). El {@code backoffMillis} es el
 * retardo base entre intentos; no se aplica una espera bloqueante en esta version
 * (se documenta el valor para el adaptador/planificador real), de modo que la
 * politica sea determinista y no introduzca esperas en pruebas.</p>
 *
 * @param maxIntentos   numero maximo de intentos de envio (&ge; 1). Por defecto 3.
 * @param backoffMillis retardo base entre intentos en milisegundos (&ge; 0). Por
 *                      defecto 1000. Informativo para el planificador de reintentos.
 */
@ConfigurationProperties(prefix = "crm.notificaciones.reintentos")
public record ReintentosProperties(Integer maxIntentos, Long backoffMillis) {

    /** Numero de intentos por defecto cuando no se configura (Req 46.3). */
    public static final int MAX_INTENTOS_POR_DEFECTO = 3;

    /** Retardo base por defecto entre intentos, en milisegundos. */
    public static final long BACKOFF_MILLIS_POR_DEFECTO = 1000L;

    /**
     * Normaliza los valores nulos a sus valores por defecto y valida los rangos
     * minimos, de modo que la politica sea siempre coherente (Req 46.3).
     *
     * @throws IllegalArgumentException si {@code maxIntentos < 1} o
     *         {@code backoffMillis < 0}.
     */
    public ReintentosProperties {
        if (maxIntentos == null) {
            maxIntentos = MAX_INTENTOS_POR_DEFECTO;
        }
        if (backoffMillis == null) {
            backoffMillis = BACKOFF_MILLIS_POR_DEFECTO;
        }
        if (maxIntentos < 1) {
            throw new IllegalArgumentException(
                    "crm.notificaciones.reintentos.max-intentos debe ser mayor o igual a 1");
        }
        if (backoffMillis < 0) {
            throw new IllegalArgumentException(
                    "crm.notificaciones.reintentos.backoff-millis no puede ser negativo");
        }
    }

    /**
     * Numero maximo de intentos de envio efectivo (nunca nulo, &ge; 1).
     *
     * @return el maximo de intentos configurado.
     */
    public int maxIntentosEfectivo() {
        return maxIntentos;
    }
}
