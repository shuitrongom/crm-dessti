package com.dessti.crm.social.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear una Plantilla_Mensaje (Req 64.7). DTO de entrada
 * del contrato REST.
 *
 * @param canal     etiqueta del Canal_Social (whatsapp/messenger/instagram); obligatorio.
 * @param nombre    nombre de la plantilla; obligatorio.
 * @param contenido contenido de la plantilla; obligatorio.
 * @param aprobada  {@code true} si la plantilla ya esta aprobada por el proveedor.
 */
public record CrearPlantillaMensajeRequest(
        @NotBlank @Size(max = 12) String canal,
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank String contenido,
        boolean aprobada) {
}
