package com.dessti.crm.calidad.application;

import com.dessti.crm.calidad.domain.TipoContexto;

/**
 * Comando de aplicacion para determinar una cuestion de
 * {@link com.dessti.crm.calidad.domain.ContextoOrganizacion} (Req 70.5). La
 * justificacion es obligatoria aun cuando {@code climaPertinente} sea {@code false}.
 *
 * @param cuestion        cuestion determinada; obligatoria.
 * @param tipo            tipo (interna/externa); obligatorio.
 * @param climaPertinente pertinencia del cambio climatico.
 * @param justificacion   justificacion de la determinacion; obligatoria.
 * @param parteInteresada parte interesada; opcional.
 * @param expectativa     expectativa de la parte interesada; opcional.
 */
public record DeterminarContextoCommand(
        String cuestion,
        TipoContexto tipo,
        boolean climaPertinente,
        String justificacion,
        String parteInteresada,
        String expectativa) {
}
