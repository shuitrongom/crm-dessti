package com.dessti.crm.presupuestos.domain;

/**
 * Tipo de renglon presupuestario que determina la <strong>convencion de signo</strong>
 * de la variacion favorable/desfavorable (Req 62.6, Property 36).
 *
 * <p>Un Presupuesto ({@code presupuesto} de V40) fija montos estimados de ingresos
 * y/o egresos. Al comparar contra el ejercicio real (agregacion de solo lectura,
 * Req 62.2), el caracter <em>favorable</em> de la desviacion depende de si el
 * renglon es de ingreso o de egreso:</p>
 *
 * <ul>
 *   <li>{@link #INGRESO}: se desea recaudar <em>mas</em> de lo presupuestado; por
 *       tanto {@code real >= presupuestado} es FAVORABLE y {@code real <
 *       presupuestado} es DESFAVORABLE.</li>
 *   <li>{@link #EGRESO}: se desea gastar <em>menos</em> de lo presupuestado; por
 *       tanto {@code real <= presupuestado} es FAVORABLE y {@code real >
 *       presupuestado} es DESFAVORABLE.</li>
 * </ul>
 *
 * <p>La variacion en importe siempre se calcula igual ({@code real - presupuestado},
 * Req 62.6); es solo la interpretacion favorable/desfavorable la que cambia segun
 * el tipo. Ver {@link CalculoVariacionPresupuesto}.</p>
 */
public enum TipoPresupuesto {

    /** Renglon de ingresos: mas real que lo presupuestado es favorable. */
    INGRESO,

    /** Renglon de egresos: menos real que lo presupuestado es favorable. */
    EGRESO
}
