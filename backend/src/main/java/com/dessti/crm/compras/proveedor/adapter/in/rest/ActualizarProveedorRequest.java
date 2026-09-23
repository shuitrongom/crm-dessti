package com.dessti.crm.compras.proveedor.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para actualizar un Proveedor (Req 29.4).
 *
 * <p>DTO de entrada del contrato REST, distinto de la entidad y del comando
 * {@link com.dessti.crm.compras.proveedor.application.ActualizarProveedorCommand}
 * (Req 12.2). El {@code tenant_id} se deriva del contexto (Req 23.4).</p>
 *
 * <p>Como en el alta, la validacion de campo se limita a presencia/longitud
 * (400); el formato del RFC/email/telefono lo valida el dominio (422).</p>
 *
 * @param nombre   nuevo nombre; obligatorio (1..200).
 * @param rfc      nuevo identificador fiscal; obligatorio (12..13).
 * @param email    nuevo correo electronico; opcional.
 * @param telefono nuevo telefono; opcional.
 */
public record ActualizarProveedorRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(min = 12, max = 13) String rfc,
        @Size(max = 254) String email,
        @Size(max = 20) String telefono) {
}
