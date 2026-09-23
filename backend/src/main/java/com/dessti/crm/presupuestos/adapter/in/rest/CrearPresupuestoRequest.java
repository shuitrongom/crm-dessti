package com.dessti.crm.presupuestos.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear un Presupuesto por area y periodo con montos
 * estimados de ingresos y/o egresos (Req 62.1). DTO de entrada del contrato REST,
 * distinto de la entidad JPA.
 *
 * @param area              area funcional (por ejemplo {@code comercial},
 *                          {@code produccion}, {@code compras}, {@code nomina});
 *                          obligatoria, hasta 40 caracteres.
 * @param periodo           periodo 'AAAA-MM' o codigo equivalente; obligatorio, hasta
 *                          7 caracteres.
 * @param ingresosEstimados ingresos estimados; obligatorio y no negativo.
 * @param egresosEstimados  egresos estimados; obligatorio y no negativo.
 */
public record CrearPresupuestoRequest(
        @NotBlank @Size(max = 40) String area,
        @NotBlank @Size(max = 7) String periodo,
        @NotNull @DecimalMin("0.00") BigDecimal ingresosEstimados,
        @NotNull @DecimalMin("0.00") BigDecimal egresosEstimados) {
}
