package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado INMUTABLE del calculo de una ENTRADA de inventario por parte del motor de
 * costeo {@link MotorCosteo} (Req 60, tarea 23.2). Agrupa el nuevo saldo del (Almacen,
 * Material) y el costeo del movimiento, para que el servicio de aplicacion solo tenga
 * que PERSISTIR estos valores ya calculados (inventario perpetuo, Req 60.10).
 *
 * <p>El motor es PURO: no muta estado ni accede a JPA/Spring. Las capas resultantes
 * ({@link #nuevasCapas()}) son la lista PEPS que el servicio reconcilia con las filas
 * {@link CapaCosto}; en {@link MetodoCosteo#PROMEDIO} el motor devuelve las capas sin
 * cambios (el metodo promedio no las usa).</p>
 *
 * <p><strong>Escalas (coherentes con V26):</strong> cantidad a escala 3, costos a
 * escala 4, redondeo {@code HALF_UP}.</p>
 *
 * @param nuevoSaldoCantidad     cantidad en existencia tras la entrada (escala 3); &gt;= 0.
 * @param nuevoCostoPromedio     costo promedio ponderado tras la entrada (escala 4); &gt;= 0.
 *                               En PEPS se mantiene como promedio de las capas restantes
 *                               para consistencia de reportes/Kardex (DECISION 23.2).
 * @param nuevasCapas            capas PEPS resultantes (nueva capa al FINAL); inmutable.
 * @param costoUnitarioMovimiento costo unitario del movimiento de entrada (escala 4).
 * @param costoTotalMovimiento   costo total del movimiento de entrada (escala 4).
 */
public record ResultadoEntrada(
        BigDecimal nuevoSaldoCantidad,
        BigDecimal nuevoCostoPromedio,
        List<CapaCostoValor> nuevasCapas,
        BigDecimal costoUnitarioMovimiento,
        BigDecimal costoTotalMovimiento) {
}
