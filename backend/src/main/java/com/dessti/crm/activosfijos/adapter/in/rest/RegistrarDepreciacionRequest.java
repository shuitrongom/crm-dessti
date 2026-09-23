package com.dessti.crm.activosfijos.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de la peticion para correr la depreciacion de un periodo de un Activo_Fijo
 * (Req 44.3). DTO de entrada del contrato REST, distinto de la entidad JPA.
 *
 * <p>El periodo es mensual con formato {@code 'AAAA-MM'} (por ejemplo
 * {@code 2025-03}); Bean Validation comprueba el patron (400) y el dominio lo
 * revalida (422).</p>
 *
 * @param periodo periodo mensual a depreciar en formato {@code 'AAAA-MM'}; obligatorio.
 */
public record RegistrarDepreciacionRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{4}-[0-9]{2}$",
                message = "El periodo debe tener el formato 'AAAA-MM'.") String periodo) {
}
