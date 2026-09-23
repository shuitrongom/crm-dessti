package com.dessti.crm.calidad.application;

import com.dessti.crm.calidad.domain.Impacto;
import com.dessti.crm.calidad.domain.Probabilidad;

/**
 * Comando de aplicacion para identificar un {@link com.dessti.crm.calidad.domain.Riesgo}
 * (Req 70.3). El nivel derivado lo calcula el dominio; no se recibe del cliente.
 *
 * @param descripcion  descripcion del riesgo; obligatoria.
 * @param probabilidad probabilidad; obligatoria.
 * @param impacto      impacto; obligatorio.
 * @param acciones     acciones para abordarlo; opcional.
 */
public record IdentificarRiesgoCommand(
        String descripcion,
        Probabilidad probabilidad,
        Impacto impacto,
        String acciones) {
}
