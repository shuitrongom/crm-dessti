package com.dessti.crm.rhnomina.nomina.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Cuerpo (opcional) de la peticion para calcular una Nomina (Req 41.1, 41.2). Aporta
 * los parametros del proceso aplicados a los Empleados del periodo: aguinaldo, PTU y
 * tasa de descuento de Infonavit. Todos son opcionales ({@code null} equivale a cero /
 * sin credito). DTO de entrada del contrato REST, distinto del comando de aplicacion
 * {@link com.dessti.crm.rhnomina.nomina.application.CalcularNominaCommand} (Req 12.2).
 *
 * @param aguinaldo     importe de aguinaldo por Empleado; opcional, no negativo.
 * @param ptu           importe de PTU por Empleado; opcional, no negativo.
 * @param tasaInfonavit tasa de descuento de Infonavit en {@code [0, 1]}; opcional.
 */
public record CalcularNominaRequest(
        @PositiveOrZero BigDecimal aguinaldo,
        @PositiveOrZero BigDecimal ptu,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal tasaInfonavit) {
}
