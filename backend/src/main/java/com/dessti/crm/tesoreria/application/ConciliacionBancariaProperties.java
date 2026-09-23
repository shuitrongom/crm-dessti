package com.dessti.crm.tesoreria.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades configurables de la conciliacion bancaria (Req 43.3).
 *
 * <p>Expone la <strong>tolerancia de fecha configurable</strong> en dias: la
 * diferencia maxima en dias admitida entre la fecha de un Movimiento_Bancario y la
 * fecha de la partida contable candidata (Poliza_Contable/Pago) para considerarlas
 * coincidentes en el emparejamiento (Req 43.3). Una tolerancia {@code 0} exige el
 * mismo dia; {@code 3} admite hasta tres dias de desfase en cualquier direccion.</p>
 *
 * <p>Se enlaza a {@code crm.tesoreria.conciliacion.*} en {@code application.yml}. El
 * valor por defecto es {@code 3} dias si no se configura externamente. Se mantiene
 * inyectable para que las pruebas puedan construirla con un valor explicito. Sigue el
 * mismo patron que {@code ConciliacionProperties} del modulo de compras.</p>
 *
 * @param toleranciaDias tolerancia de fecha en dias; por defecto {@code 3}. Debe ser
 *                       no negativa.
 */
@ConfigurationProperties(prefix = "crm.tesoreria.conciliacion")
public record ConciliacionBancariaProperties(Integer toleranciaDias) {

    /** Tolerancia de fecha por defecto (3 dias) si no se configura externamente. */
    private static final int TOLERANCIA_POR_DEFECTO = 3;

    /**
     * Aplica el valor por defecto cuando no se provee y valida que la tolerancia no
     * sea negativa.
     *
     * @throws IllegalArgumentException si la tolerancia es negativa.
     */
    public ConciliacionBancariaProperties {
        if (toleranciaDias == null) {
            toleranciaDias = TOLERANCIA_POR_DEFECTO;
        }
        if (toleranciaDias < 0) {
            throw new IllegalArgumentException(
                    "crm.tesoreria.conciliacion.tolerancia-dias no puede ser negativa");
        }
    }
}
