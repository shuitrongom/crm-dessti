package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de aplicacion para registrar un movimiento (ENTRADA o SALIDA) de inventario por
 * Almacen con costeo (Req 60, tarea 23.2). Transporta los datos de entrada del caso de uso,
 * desacoplados del contrato REST y de las entidades JPA. La validacion de negocio (cantidad
 * &gt; 0, costo &gt;= 0, existencias suficientes, control de lote) la refuerzan el dominio y el
 * {@code MotorCosteo}.
 *
 * <p>El {@code costoUnitario} solo aplica a las ENTRADAS: en una SALIDA el costo lo determina
 * el metodo de costeo del Material (promedio vigente o consumo de capas PEPS), por lo que
 * puede viajar {@code null}. El {@code loteCodigo} es opcional; cuando el Material tiene el
 * control de lote habilitado y se informa, el servicio hace upsert del Lote y lo asocia al
 * movimiento (Req 60.4, trazabilidad).</p>
 *
 * @param almacenId     Almacen del movimiento; obligatorio.
 * @param materialId    Material afectado; obligatorio.
 * @param loteCodigo    codigo del Lote a asociar; opcional ({@code null}/blanco = sin lote).
 * @param cantidad      cantidad del movimiento; obligatoria y &gt; 0.
 * @param costoUnitario costo unitario (solo ENTRADAS); &gt;= 0 o {@code null} en salidas.
 * @param motivo        nota opcional del movimiento.
 */
public record RegistrarMovimientoAlmacenCommand(
        UUID almacenId,
        UUID materialId,
        String loteCodigo,
        BigDecimal cantidad,
        BigDecimal costoUnitario,
        String motivo) {
}
