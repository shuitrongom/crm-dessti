package com.dessti.crm.comercial.cliente.application;

import java.util.UUID;

/**
 * Comando de asociacion de un {@link com.dessti.crm.comercial.cliente.domain.Contacto}
 * a un Cliente activo (Req 5.5, 5.6). Distinto de la entidad; el {@code tenant_id}
 * se deriva del contexto (Req 23.4).
 *
 * @param clienteId identificador del Cliente propietario (debe existir y estar
 *                  activo en el tenant vigente).
 * @param nombre    nombre del Contacto; obligatorio (1..200).
 * @param email     correo electronico; opcional (valido si se proporciona).
 * @param telefono  telefono; opcional (10..15 digitos si se proporciona).
 */
public record CrearContactoCommand(
        UUID clienteId,
        String nombre,
        String email,
        String telefono) {
}
