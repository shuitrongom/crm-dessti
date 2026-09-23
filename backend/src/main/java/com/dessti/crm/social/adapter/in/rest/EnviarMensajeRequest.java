package com.dessti.crm.social.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para enviar un Mensaje_Social en una Conversacion (Req
 * 64.6, 64.7, 64.8, 64.11). DTO de entrada del contrato REST, distinto de la
 * entidad JPA. Las guardas de Ventana_Servicio y Opt_In las aplica la capa de
 * aplicacion.
 *
 * @param tipo        etiqueta del tipo (texto/plantilla/interactivo); obligatorio.
 * @param contenido   contenido del mensaje o nombre/cuerpo de la Plantilla_Mensaje; obligatorio.
 * @param esMarketing {@code true} si es un mensaje de marketing (sujeto a Opt_In, Req 64.8).
 */
public record EnviarMensajeRequest(
        @NotBlank String tipo,
        @NotBlank String contenido,
        boolean esMarketing) {
}
