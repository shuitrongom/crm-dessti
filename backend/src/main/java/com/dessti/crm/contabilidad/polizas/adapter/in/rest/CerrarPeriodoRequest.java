package com.dessti.crm.contabilidad.polizas.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para cerrar un periodo contable mensual. El {@code tenant_id}
 * y el actor se derivan del contexto autenticado (Req 23.4).
 *
 * @param anio anio del periodo (2000..2100); obligatorio.
 * @param mes  mes del periodo (1..12); obligatorio.
 */
public record CerrarPeriodoRequest(
        @NotNull @Min(2000) @Max(2100) Integer anio,
        @NotNull @Min(1) @Max(12) Integer mes) {
}
