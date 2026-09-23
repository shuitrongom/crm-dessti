package com.dessti.crm.social.adapter.in.rest;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.EstadoPublicacion;

/**
 * Utilidades de parseo de etiquetas del bloque de Publicacion_Social y
 * Campaña_Publicitaria (Req 65) hacia sus enums de dominio. Complementa a
 * {@code ParseoSocial} (bloque 40) sin modificarlo: aqui viven exclusivamente los
 * parseos propios de este bloque ({@link EstadoPublicacion} y el reuso del
 * {@link CanalSocial}). Una etiqueta nula, en blanco o desconocida se traduce a
 * {@link ReglaNegocioException} (HTTP 422), coherente con el resto del contrato REST.
 */
final class ParseoPublicacion {

    private ParseoPublicacion() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Interpreta una etiqueta como {@link CanalSocial}; opcional cuando se admite
     * ausencia de filtro.
     *
     * @param etiqueta etiqueta del canal; {@code null}/blanco devuelve {@code null}.
     * @return el canal, o {@code null} si no se indico.
     * @throws ReglaNegocioException si la etiqueta es desconocida (422).
     */
    static CanalSocial canalOpcional(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        try {
            return CanalSocial.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Canal_Social desconocido: " + etiqueta);
        }
    }

    /**
     * Interpreta una etiqueta como {@link EstadoPublicacion}; opcional cuando se
     * admite ausencia de filtro.
     *
     * @param etiqueta etiqueta del estado; {@code null}/blanco devuelve {@code null}.
     * @return el estado, o {@code null} si no se indico.
     * @throws ReglaNegocioException si la etiqueta es desconocida (422).
     */
    static EstadoPublicacion estadoPublicacionOpcional(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        return estadoPublicacionRequerido(etiqueta);
    }

    /**
     * Interpreta una etiqueta como {@link EstadoPublicacion} obligatorio.
     *
     * @param etiqueta etiqueta del estado; obligatorio.
     * @return el estado.
     * @throws ReglaNegocioException si la etiqueta falta o es desconocida (422).
     */
    static EstadoPublicacion estadoPublicacionRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado de la Publicacion_Social es obligatorio.");
        }
        try {
            return EstadoPublicacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Publicacion_Social desconocido: " + etiqueta);
        }
    }
}
