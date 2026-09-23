package com.dessti.crm.social.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para registrar un Opt_In u Opt_Out (Req 64.9). DTO de
 * entrada del contrato REST.
 *
 * @param canal         etiqueta del Canal_Social (whatsapp/messenger/instagram); obligatorio.
 * @param sujetoExterno identificador del sujeto en el canal (remitente); obligatorio.
 * @param clienteId     Cliente asociado; opcional ({@code null}).
 * @param estado        etiqueta del estado (opt_in/opt_out); obligatorio.
 */
public record RegistrarConsentimientoRequest(
        @NotBlank String canal,
        @NotBlank String sujetoExterno,
        UUID clienteId,
        @NotBlank String estado) {
}
