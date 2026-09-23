package com.dessti.crm.comercial.cliente.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para asociar un Contacto a un Cliente activo
 * (Req 5.5, tarea 15.2).
 *
 * <p>DTO de entrada del contrato REST, distinto de la entidad y del comando
 * {@link com.dessti.crm.comercial.cliente.application.CrearContactoCommand}
 * (Req 12.2). El identificador del Cliente propietario NO viaja en el cuerpo:
 * se toma de la ruta ({@code /clientes/{id}/contactos}). El {@code tenant_id} se
 * deriva del contexto (Req 23.4).</p>
 *
 * <p>La validacion de campo se limita a presencia/longitud (400); el formato del
 * email/telefono lo valida el dominio (422).</p>
 *
 * @param nombre   nombre del Contacto; obligatorio (1..200).
 * @param email    correo electronico; opcional.
 * @param telefono telefono; opcional.
 */
public record CrearContactoRequest(
        @NotBlank @Size(max = 200) String nombre,
        @Size(max = 254) String email,
        @Size(max = 20) String telefono) {
}
