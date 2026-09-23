package com.dessti.crm.platform.empresas.rest;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para extender el periodo de prueba de un Contrato
 * (Req 8; la nueva fecha queda acotada a la duracion del paquete en el
 * servicio).
 *
 * @param nuevaVigenciaFin nuevo fin de vigencia de la prueba; obligatorio.
 */
public record ExtenderPruebaRequest(
        @NotNull LocalDate nuevaVigenciaFin) {
}
