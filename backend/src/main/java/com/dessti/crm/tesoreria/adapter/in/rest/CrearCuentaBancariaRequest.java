package com.dessti.crm.tesoreria.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta una Cuenta_Bancaria (Req 43.1). DTO de
 * entrada del contrato REST, distinto del comando de aplicacion
 * {@link com.dessti.crm.tesoreria.application.CrearCuentaBancariaCommand} (Req 12.2).
 * El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * @param nombre nombre descriptivo; obligatorio y no vacio (max 200).
 * @param banco  banco de la cuenta; obligatorio y no vacio (max 120).
 * @param clabe  CLABE interbancaria (18 digitos); opcional.
 * @param moneda moneda ISO 4217 (3 letras); opcional (por defecto {@code MXN}).
 */
public record CrearCuentaBancariaRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(max = 120) String banco,
        @Pattern(regexp = "\\d{18}", message = "La CLABE debe tener 18 digitos") String clabe,
        @Size(min = 3, max = 3) String moneda) {
}
