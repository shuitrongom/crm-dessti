package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para AJUSTAR el inventario de un Material en un Almacen por conteo
 * fisico (Req 60). DTO de entrada del contrato REST, distinto de la entidad JPA. El Almacen
 * viaja en la ruta.
 *
 * <p>El ajuste concilia el saldo del sistema con la {@code cantidadContada} fisicamente: el
 * servicio calcula la diferencia contra el saldo vigente y genera internamente una ENTRADA
 * o SALIDA de tipo {@code ajuste} por esa diferencia, conservando el costo promedio vigente
 * (no altera la valuacion unitaria). Si la cantidad contada coincide con el saldo, no se
 * registra movimiento.</p>
 *
 * @param materialId      Material a ajustar; obligatorio.
 * @param cantidadContada cantidad real contada en el conteo fisico; obligatoria y &gt;= 0
 *                        (un conteo de 0 significa que no queda existencia).
 * @param motivo          nota/justificacion del ajuste; opcional pero recomendada.
 */
public record AjustarInventarioRequest(
        @NotNull UUID materialId,
        @NotNull @DecimalMin(value = "0") BigDecimal cantidadContada,
        String motivo) {
}
