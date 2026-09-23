package com.dessti.crm.calidad.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para proponer un Cambio_SGC (Req 70.4). Todos los campos son
 * obligatorios porque se exigen antes de aprobar.
 *
 * @param titulo                    titulo del cambio; obligatorio.
 * @param proposito                 proposito; obligatorio.
 * @param consecuenciasPotenciales  consecuencias potenciales; obligatorias.
 * @param recursosNecesarios        recursos necesarios; obligatorios.
 * @param responsableId             Usuario responsable; obligatorio.
 */
public record ProponerCambioSgcRequest(
        @NotBlank String titulo,
        @NotBlank String proposito,
        @NotBlank String consecuenciasPotenciales,
        @NotBlank String recursosNecesarios,
        @NotNull UUID responsableId) {
}
