package com.dessti.crm.presupuestos.application;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades configurables del modulo de Presupuestos (Req 62.3).
 *
 * <p>Expone el <strong>umbral de desviacion configurable</strong> (Req 62.3): la
 * fraccion decimal a partir de la cual una variacion de presupuesto se considera una
 * desviacion relevante que debe <em>destacarse</em> en reportes. Se interpreta como
 * una fraccion RELATIVA al presupuesto: un umbral {@code 0.10} equivale a un 10%; una
 * variacion porcentual (en valor absoluto) mayor a ese umbral marca la desviacion
 * como destacada.</p>
 *
 * <p>Se enlaza a {@code crm.presupuestos.*} en {@code application.yml}. El valor por
 * defecto es {@code 0.10} (10%) si no se configura externamente. Se mantiene
 * inyectable para que las pruebas puedan construirla con un valor explicito. Sigue el
 * mismo patron que {@code ConciliacionProperties} / {@code AuditoriaProperties} de la
 * plataforma.</p>
 *
 * @param umbralDesviacion fraccion decimal del umbral de desviacion (por ejemplo
 *                         {@code 0.10} = 10%); por defecto {@code 0.10}. Debe ser no
 *                         negativa.
 */
@ConfigurationProperties(prefix = "crm.presupuestos")
public record PresupuestosProperties(BigDecimal umbralDesviacion) {

    /** Umbral de desviacion por defecto (10%) si no se configura externamente. */
    private static final BigDecimal UMBRAL_POR_DEFECTO = new BigDecimal("0.10");

    /**
     * Aplica el valor por defecto cuando no se provee y valida que el umbral no sea
     * negativo.
     *
     * @throws IllegalArgumentException si el umbral es negativo.
     */
    public PresupuestosProperties {
        if (umbralDesviacion == null) {
            umbralDesviacion = UMBRAL_POR_DEFECTO;
        }
        if (umbralDesviacion.signum() < 0) {
            throw new IllegalArgumentException(
                    "crm.presupuestos.umbral-desviacion no puede ser negativa");
        }
    }
}
