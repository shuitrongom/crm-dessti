package com.dessti.crm.contabilidad.polizas.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para reabrir un periodo contable mensual cerrado. La
 * reapertura exige un motivo obligatorio (queda auditado). El {@code tenant_id} y el
 * actor se derivan del contexto autenticado (Req 23.4).
 *
 * @param anio   anio del periodo (2000..2100); obligatorio.
 * @param mes    mes del periodo (1..12); obligatorio.
 * @param motivo motivo de la reapertura; obligatorio y no vacio.
 */
public record ReabrirPeriodoRequest(
        @NotNull @Min(2000) @Max(2100) Integer anio,
        @NotNull @Min(1) @Max(12) Integer mes,
        @NotBlank @Size(max = 500) String motivo) {
}
