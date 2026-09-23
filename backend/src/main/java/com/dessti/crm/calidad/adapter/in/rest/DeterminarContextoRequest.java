package com.dessti.crm.calidad.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para determinar una cuestion de Contexto_Organizacion (Req 70.5).
 * La justificacion es obligatoria aun cuando {@code climaPertinente} sea {@code false}.
 *
 * @param cuestion        cuestion determinada; obligatoria.
 * @param tipo            etiqueta del tipo (interna/externa); obligatoria.
 * @param climaPertinente pertinencia del cambio climatico.
 * @param justificacion   justificacion de la determinacion; obligatoria.
 * @param parteInteresada parte interesada; opcional.
 * @param expectativa     expectativa de la parte interesada; opcional.
 */
public record DeterminarContextoRequest(
        @NotBlank String cuestion,
        @NotBlank String tipo,
        boolean climaPertinente,
        @NotBlank String justificacion,
        String parteInteresada,
        String expectativa) {
}
