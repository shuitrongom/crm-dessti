package com.dessti.crm.comercial.cotizacion.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo OPCIONAL de la peticion para enviar una Cotizacion por correo (V60). Si
 * se indica {@code email}, se usa como destinatario; si el cuerpo es nulo o el
 * correo esta vacio, el servidor usa el correo del Cliente de la Cotizacion.
 *
 * @param email correo destino opcional; debe tener formato de correo si se indica.
 */
public record EnviarCorreoRequest(
        @Email @Size(max = 320) String email) {
}
