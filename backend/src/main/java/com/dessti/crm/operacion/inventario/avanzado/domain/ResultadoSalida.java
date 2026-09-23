package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado INMUTABLE del calculo de una SALIDA de inventario por parte del motor de
 * costeo {@link MotorCosteo} (Req 60, tarea 23.2). Agrupa el nuevo saldo del (Almacen,
 * Material) y el costeo del movimiento, para que el servicio de aplicacion solo tenga
 * que PERSISTIR estos valores ya calculados (inventario perpetuo, Req 60.10) y reconciliar
 * las capas PEPS consumidas.
 *
 * <p>El motor es PURO: no muta estado ni accede a JPA/Spring. En {@link MetodoCosteo#PEPS}
 * el costo de la salida sale de consumir las capas mas antiguas primero (Req 60.11) y
 * {@link #nuevasCapas()} son las capas que quedan (la primera parcialmente consumida
 * reducida, las totalmente consumidas eliminadas). En {@link MetodoCosteo#PROMEDIO} el
 * costo de la salida es el costo promedio vigente y las capas no cambian.</p>
 *
 * <p><strong>Escalas (coherentes con V26):</strong> cantidad a escala 3, costos a
 * escala 4, redondeo {@code HALF_UP}.</p>
 *
 * @param nuevoSaldoCantidad      cantidad en existencia tras la salida (escala 3); &gt;= 0.
 * @param nuevoCostoPromedio      costo promedio ponderado tras la salida (escala 4); &gt;= 0.
 *                                En PEPS es el promedio de las capas restantes; en PROMEDIO
 *                                queda inalterado (una salida no cambia el promedio).
 * @param nuevasCapas             capas PEPS resultantes tras el consumo FIFO; inmutable.
 * @param costoUnitarioMovimiento costo unitario del movimiento de salida (escala 4).
 * @param costoTotalMovimiento    costo total del movimiento de salida (escala 4).
 */
public record ResultadoSalida(
        BigDecimal nuevoSaldoCantidad,
        BigDecimal nuevoCostoPromedio,
        List<CapaCostoValor> nuevasCapas,
        BigDecimal costoUnitarioMovimiento,
        BigDecimal costoTotalMovimiento) {
}
