package com.dessti.crm.calidad.application;

import java.util.UUID;

/**
 * Comando de aplicacion para proponer un {@link com.dessti.crm.calidad.domain.CambioSgc}
 * (Req 70.4). Todos los campos son obligatorios porque se exigen antes de aprobar.
 *
 * @param titulo                    titulo del cambio; obligatorio.
 * @param proposito                 proposito; obligatorio.
 * @param consecuenciasPotenciales  consecuencias potenciales; obligatorias.
 * @param recursosNecesarios        recursos necesarios; obligatorios.
 * @param responsableId             Usuario responsable; obligatorio.
 */
public record ProponerCambioSgcCommand(
        String titulo,
        String proposito,
        String consecuenciasPotenciales,
        String recursosNecesarios,
        UUID responsableId) {
}
