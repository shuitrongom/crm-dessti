package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO de salida del RESUMEN GLOBAL del inventario avanzado del tenant vigente (Req 60, 22),
 * calculado ENTERAMENTE en el servidor sobre el saldo perpetuo {@code existencia_almacen}
 * (no en el cliente). Concentra el valor monetario total del inventario, el numero de
 * Almacenes con existencias y el desglose de cantidad y valuacion por Almacen, para alimentar
 * el tablero de Inventario Avanzado sin que la UI tenga que agregar cifra por cifra.
 *
 * @param valuacionTotal          valor monetario total del inventario ({@code SUM(cantidad*costo)}).
 * @param almacenesConExistencias numero de Almacenes distintos con saldo registrado.
 * @param porAlmacen              desglose por Almacen (cantidad total y valuacion), ordenado
 *                                por valuacion descendente.
 */
public record ResumenInventarioDto(
        BigDecimal valuacionTotal,
        long almacenesConExistencias,
        List<ResumenAlmacenDto> porAlmacen) {

    /**
     * Desglose de un Almacen dentro del resumen: cantidad total en existencia y su valuacion.
     *
     * @param almacenId    identificador del Almacen.
     * @param nombre       nombre del Almacen (o {@code null} si ya no es accesible).
     * @param cantidadTotal cantidad total en existencia en el Almacen.
     * @param valuacion    valuacion del inventario del Almacen ({@code SUM(cantidad*costo)}).
     */
    public record ResumenAlmacenDto(
            UUID almacenId,
            String nombre,
            BigDecimal cantidadTotal,
            BigDecimal valuacion) {
    }
}
