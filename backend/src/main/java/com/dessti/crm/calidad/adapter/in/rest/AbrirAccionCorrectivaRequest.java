package com.dessti.crm.calidad.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para abrir una Accion_Correctiva (Req 70.2).
 *
 * @param noConformidadId      No_Conformidad de origen; opcional.
 * @param responsableId        Usuario responsable; obligatorio.
 * @param causaRaiz            causa raiz identificada; obligatoria.
 * @param accionesPlanificadas acciones planificadas; obligatorias.
 */
public record AbrirAccionCorrectivaRequest(
        UUID noConformidadId,
        @NotNull UUID responsableId,
        @NotBlank String causaRaiz,
        @NotBlank String accionesPlanificadas) {
}
