package com.dessti.crm.calidad.application;

/**
 * Comando de aplicacion para identificar una
 * {@link com.dessti.crm.calidad.domain.OportunidadCalidad} (Req 70.3).
 *
 * @param descripcion       descripcion; obligatoria.
 * @param beneficioEsperado beneficio esperado; obligatorio.
 * @param acciones          acciones para aprovecharla; opcional.
 */
public record IdentificarOportunidadCalidadCommand(
        String descripcion,
        String beneficioEsperado,
        String acciones) {
}
