package com.dessti.crm.calidad.application;

import java.util.UUID;

/**
 * Comando de aplicacion para abrir una
 * {@link com.dessti.crm.calidad.domain.AccionCorrectiva} (Req 70.2).
 *
 * @param noConformidadId      No_Conformidad de origen; opcional.
 * @param responsableId        Usuario responsable; obligatorio.
 * @param causaRaiz            causa raiz identificada; obligatoria.
 * @param accionesPlanificadas acciones planificadas; obligatorias.
 */
public record AbrirAccionCorrectivaCommand(
        UUID noConformidadId,
        UUID responsableId,
        String causaRaiz,
        String accionesPlanificadas) {
}
