package com.dessti.crm.platform.empresas.rest;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para asociar una Suscripcion entre una Empresa y un Plan
 * (Req 25.2, tarea 14.2). La Suscripcion se crea en estado {@code activa}.
 *
 * @param tenantId       Empresa (tenant) titular; obligatorio.
 * @param planId         Plan a contratar; obligatorio.
 * @param vigenciaInicio inicio de vigencia; opcional (si es {@code null} se toma
 *                       la fecha actual).
 * @param vigenciaFin    fin de vigencia; opcional ({@code null} = sin fin).
 */
public record CrearSuscripcionRequest(
        @NotNull UUID tenantId,
        @NotNull UUID planId,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFin) {
}
