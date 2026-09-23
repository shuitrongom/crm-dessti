package com.dessti.crm.presupuestos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Nucleo de calculo <strong>puro</strong> de la variacion de presupuesto (Req 62.6,
 * 62.7, 62.3; <strong>Property 36</strong>). No depende de Spring ni de JPA: opera
 * exclusivamente sobre {@link BigDecimal} con escala monetaria 2 y redondeo
 * {@link RoundingMode#HALF_UP}, de forma determinista. Es la pieza reutilizable que
 * el servicio de aplicacion invoca para construir el reporte de variacion sin
 * modificar ningun origen de datos (agregacion de solo lectura, Req 62.2).
 *
 * <h2>Definiciones (Property 36)</h2>
 * <ul>
 *   <li><strong>Variacion en importe (Req 62.6, 62.7):</strong>
 *       {@code variacionImporte = round(real - presupuestado, 2, HALF_UP)}. Es la
 *       diferencia absoluta entre lo ejercido realmente y lo presupuestado.</li>
 *   <li><strong>Variacion en porcentaje (Req 62.7):</strong> respecto al
 *       presupuesto,
 *       {@code variacionPorcentaje = round(variacionImporte / presupuestado * 100,
 *       2, HALF_UP)} cuando {@code presupuestado > 0}.</li>
 *   <li><strong>Favorable / desfavorable (Req 62.6):</strong> depende del
 *       {@link TipoPresupuesto}:
 *       <ul>
 *         <li>{@link TipoPresupuesto#INGRESO}: {@code real >= presupuestado} es
 *             FAVORABLE (mas ingreso del planeado); {@code real < presupuestado}
 *             es DESFAVORABLE.</li>
 *         <li>{@link TipoPresupuesto#EGRESO}: {@code real <= presupuestado} es
 *             FAVORABLE (gasto menor al planeado); {@code real > presupuestado}
 *             es DESFAVORABLE.</li>
 *       </ul></li>
 *   <li><strong>Supera umbral (Req 62.3):</strong>
 *       {@code abs(variacionPorcentaje) > umbral * 100}. Cuando se cumple, la
 *       desviacion debe destacarse en reportes.</li>
 * </ul>
 *
 * <h2>Regla documentada de presupuesto cero (division por cero, Req 62.7)</h2>
 * <p>Cuando {@code presupuestado <= 0} el porcentaje respecto al presupuesto no esta
 * matematicamente definido (division por cero). Para mantener el calculo total y
 * determinista se adopta la convencion: <strong>si {@code presupuestado <= 0} el
 * {@code variacionPorcentaje} es 0</strong> (no hay base contra la cual medir el
 * porcentaje). En ese caso {@code superaUmbral} tambien es {@code false} (0 no supera
 * ningun umbral no negativo). El {@code variacionImporte} se sigue calculando con
 * normalidad ({@code round(real - 0, 2)} = {@code round(real, 2)}) y el caracter
 * favorable/desfavorable se evalua igual segun el tipo (por ejemplo, para un ingreso
 * con presupuesto 0, cualquier real {@code >= 0} es favorable).</p>
 *
 * <p>El {@code umbral} se interpreta como una FRACCION (por ejemplo {@code 0.10} =
 * 10%), coherente con {@code crm.presupuestos.umbral-desviacion}. La clase es una
 * utilidad estatica sin estado.</p>
 */
public final class CalculoVariacionPresupuesto {

    /** Escala monetaria y porcentual: 2 decimales, redondeo al mas cercano. */
    public static final int ESCALA = 2;

    /** Factor de 100 para expresar la fraccion como porcentaje. */
    private static final BigDecimal CIEN = new BigDecimal("100");

    private CalculoVariacionPresupuesto() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Redondea un monto a la escala monetaria estandar (2, HALF_UP). Utilidad
     * reutilizable para normalizar montos presupuestados y reales antes de calcular.
     *
     * @param monto monto a normalizar; obligatorio (no nulo).
     * @return el monto con escala 2 y redondeo HALF_UP.
     * @throws NullPointerException si {@code monto} es nulo.
     */
    public static BigDecimal normalizar(BigDecimal monto) {
        return monto.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /**
     * Calcula la variacion completa de un renglon presupuestario (Property 36).
     *
     * @param presupuestado importe presupuestado (estimado) del renglon; obligatorio.
     * @param real          importe real ejercido del renglon (agregacion de solo
     *                      lectura, Req 62.2); obligatorio.
     * @param tipo          {@link TipoPresupuesto} que fija la convencion de signo
     *                      favorable/desfavorable (Req 62.6); obligatorio.
     * @param umbral        fraccion de desviacion a partir de la cual se destaca la
     *                      variacion (por ejemplo {@code 0.10} = 10%, Req 62.3);
     *                      obligatorio y no negativo.
     * @return el {@link ResultadoVariacion} inmutable con importe, porcentaje,
     *         favorable y superaUmbral.
     * @throws NullPointerException     si algun argumento es nulo.
     * @throws IllegalArgumentException si {@code umbral} es negativo.
     */
    public static ResultadoVariacion calcular(
            BigDecimal presupuestado, BigDecimal real, TipoPresupuesto tipo, BigDecimal umbral) {
        if (presupuestado == null || real == null || tipo == null || umbral == null) {
            throw new NullPointerException(
                    "presupuestado, real, tipo y umbral son obligatorios para calcular la variacion");
        }
        if (umbral.signum() < 0) {
            throw new IllegalArgumentException("El umbral de desviacion no puede ser negativo");
        }

        BigDecimal presupuestadoNorm = normalizar(presupuestado);
        BigDecimal realNorm = normalizar(real);

        // Variacion en importe absoluto: real - presupuestado (Req 62.6, 62.7).
        BigDecimal variacionImporte = normalizar(realNorm.subtract(presupuestadoNorm));

        // Variacion en porcentaje respecto al presupuesto (Req 62.7). Regla de
        // presupuesto cero documentada: porcentaje 0 para evitar division por cero.
        BigDecimal variacionPorcentaje;
        if (presupuestadoNorm.signum() > 0) {
            variacionPorcentaje = variacionImporte
                    .multiply(CIEN)
                    .divide(presupuestadoNorm, ESCALA, RoundingMode.HALF_UP);
        } else {
            variacionPorcentaje = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);
        }

        boolean favorable = esFavorable(presupuestadoNorm, realNorm, tipo);
        boolean superaUmbral = superaUmbral(variacionPorcentaje, umbral);

        return new ResultadoVariacion(variacionImporte, variacionPorcentaje, favorable, superaUmbral);
    }

    /**
     * Determina si la desviacion es favorable segun la convencion de signo del
     * {@link TipoPresupuesto} (Req 62.6). Para INGRESO, favorable es
     * {@code real >= presupuestado}; para EGRESO, favorable es
     * {@code real <= presupuestado}. La comparacion usa {@link BigDecimal#compareTo}
     * para ignorar diferencias de escala.
     *
     * @param presupuestado importe presupuestado normalizado.
     * @param real          importe real normalizado.
     * @param tipo          tipo de renglon.
     * @return {@code true} si la desviacion es favorable.
     */
    public static boolean esFavorable(BigDecimal presupuestado, BigDecimal real, TipoPresupuesto tipo) {
        int comparacion = real.compareTo(presupuestado);
        return switch (tipo) {
            case INGRESO -> comparacion >= 0;
            case EGRESO -> comparacion <= 0;
        };
    }

    /**
     * Indica si la variacion porcentual supera el umbral configurable y por tanto
     * debe destacarse en reportes (Req 62.3): {@code abs(variacionPorcentaje) >
     * umbral * 100}. El umbral se expresa como fraccion (0.10 = 10%), por lo que se
     * lleva a puntos porcentuales multiplicando por 100 antes de comparar contra el
     * porcentaje (que ya esta en puntos porcentuales).
     *
     * @param variacionPorcentaje variacion porcentual (en puntos porcentuales).
     * @param umbral               fraccion de umbral (por ejemplo 0.10); no negativa.
     * @return {@code true} si el valor absoluto del porcentaje supera el umbral.
     */
    public static boolean superaUmbral(BigDecimal variacionPorcentaje, BigDecimal umbral) {
        BigDecimal umbralEnPuntos = umbral.multiply(CIEN);
        return variacionPorcentaje.abs().compareTo(umbralEnPuntos) > 0;
    }
}
