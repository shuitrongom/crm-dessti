package com.dessti.crm.presupuestos.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para actualizar los montos estimados de un Presupuesto
 * (Req 62.5). El area y el periodo son la identidad de negocio y no se modifican, por
 * lo que no forman parte de este cuerpo. DTO de entrada del contrato REST, distinto de
 * la entidad JPA.
 *
 * @param ingresosEstimados nuevos ingresos estimados; obligatorio y no negativo.
 * @param egresosEstimados  nuevos egresos estimados; obligatorio y no negativo.
 */
public record ActualizarPresupuestoRequest(
        @NotNull @DecimalMin("0.00") BigDecimal ingresosEstimados,
        @NotNull @DecimalMin("0.00") BigDecimal egresosEstimados) {
}
