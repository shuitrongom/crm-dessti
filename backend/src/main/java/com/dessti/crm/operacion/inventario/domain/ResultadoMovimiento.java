package com.dessti.crm.operacion.inventario.domain;

import java.math.BigDecimal;

/**
 * Resultado inmutable de aplicar un movimiento de inventario sobre un
 * {@link Material} (Req 18.2, 18.5). Lo devuelve {@link Material#aplicarMovimiento}
 * para que la capa de aplicacion pueda, en una sola operacion de dominio, conocer:
 *
 * <ul>
 *   <li>las {@code existenciasResultantes} tras aplicar el movimiento (snapshot que
 *       se persiste en el historial {@code movimiento_inventario}, base del Kardex,
 *       Req 18.2);</li>
 *   <li>si el Material quedo en {@code stockBajo} —esto es, con existencias por
 *       debajo de su stock minimo— para disparar la notificacion del Req 18.5.</li>
 * </ul>
 *
 * <p>Es un objeto de valor puro, sin dependencias de framework, que hace de
 * {@link Material#aplicarMovimiento} una funcion facilmente verificable (Property 9).</p>
 *
 * @param existenciasResultantes existencias del Material tras aplicar el movimiento
 *                               (nunca negativas, Property 9).
 * @param stockBajo              {@code true} si {@code existenciasResultantes} quedo
 *                               por debajo del stock minimo del Material (Req 18.5).
 */
public record ResultadoMovimiento(BigDecimal existenciasResultantes, boolean stockBajo) {
}
