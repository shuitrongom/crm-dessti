package com.dessti.crm.presupuestos.domain;

import java.math.BigDecimal;

/**
 * Resultado inmutable del calculo de variacion de un renglon presupuestario
 * (Req 62.6, 62.7, 62.3; Property 36). Es un objeto de valor puro, sin
 * dependencias de framework ni de persistencia, producido por
 * {@link CalculoVariacionPresupuesto}.
 *
 * <p>Modela la comparacion de un <em>importe presupuestado</em> frente a un
 * <em>importe real</em> (derivado de operaciones como agregacion de solo lectura,
 * Req 62.2) para un {@link TipoPresupuesto} dado:</p>
 *
 * <ul>
 *   <li>{@code variacionImporte}: variacion en importe ABSOLUTO, es decir
 *       {@code round(real - presupuestado, 2, HALF_UP)} (Req 62.6, 62.7). Puede
 *       ser positiva, negativa o cero; su signo aritmetico NO implica por si solo
 *       favorable/desfavorable (eso lo indica {@link #favorable}).</li>
 *   <li>{@code variacionPorcentaje}: variacion en PORCENTAJE respecto al
 *       presupuesto, es decir {@code round(variacionImporte / presupuestado * 100,
 *       2, HALF_UP)} cuando {@code presupuestado > 0}; con presupuesto cero se
 *       aplica la regla documentada en {@link CalculoVariacionPresupuesto}
 *       (porcentaje 0 para evitar division por cero) (Req 62.7).</li>
 *   <li>{@code favorable}: {@code true} si la desviacion es favorable segun la
 *       convencion de signo del {@link TipoPresupuesto} (Req 62.6). Ver
 *       {@link TipoPresupuesto}.</li>
 *   <li>{@code superaUmbral}: {@code true} si {@code abs(variacionPorcentaje)}
 *       supera el umbral configurable, de modo que la desviacion deba
 *       <em>destacarse</em> en reportes (Req 62.3).</li>
 * </ul>
 *
 * @param variacionImporte    variacion en importe absoluto ({@code real - presupuestado}),
 *                            escala 2 HALF_UP (Req 62.6, 62.7).
 * @param variacionPorcentaje variacion porcentual respecto al presupuesto, escala 2
 *                            HALF_UP (Req 62.7).
 * @param favorable           {@code true} si la desviacion es favorable segun el tipo (Req 62.6).
 * @param superaUmbral        {@code true} si {@code abs(porcentaje)} supera el umbral (Req 62.3).
 */
public record ResultadoVariacion(
        BigDecimal variacionImporte,
        BigDecimal variacionPorcentaje,
        boolean favorable,
        boolean superaUmbral) {
}
