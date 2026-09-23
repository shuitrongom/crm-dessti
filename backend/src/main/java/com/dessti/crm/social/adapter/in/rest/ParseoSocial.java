package com.dessti.crm.social.adapter.in.rest;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.EstadoConsentimiento;
import com.dessti.crm.social.domain.EstadoConversacion;
import com.dessti.crm.social.domain.TipoMensaje;

/**
 * Utilidades de parseo de etiquetas de negocio de los controladores del modulo
 * {@code social} hacia sus enums de dominio. Una etiqueta nula, en blanco o
 * desconocida se traduce a {@link ReglaNegocioException} (HTTP 422), coherente con
 * el resto del contrato REST.
 */
final class ParseoSocial {

    private ParseoSocial() {
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
        return canalRequerido(etiqueta);
    }

    /**
     * Interpreta una etiqueta como {@link CanalSocial} obligatorio.
     *
     * @param etiqueta etiqueta del canal; obligatorio.
     * @return el canal.
     * @throws ReglaNegocioException si la etiqueta falta o es desconocida (422).
     */
    static CanalSocial canalRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El Canal_Social es obligatorio.");
        }
        try {
            return CanalSocial.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Canal_Social desconocido: " + etiqueta);
        }
    }

    /**
     * Interpreta una etiqueta como {@link TipoMensaje} obligatorio.
     *
     * @param etiqueta etiqueta del tipo; obligatorio.
     * @return el tipo.
     * @throws ReglaNegocioException si la etiqueta falta o es desconocida (422).
     */
    static TipoMensaje tipoRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El tipo del Mensaje_Social es obligatorio.");
        }
        try {
            return TipoMensaje.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Tipo de Mensaje_Social desconocido: " + etiqueta);
        }
    }

    /**
     * Interpreta una etiqueta como {@link EstadoConsentimiento} obligatorio.
     *
     * @param etiqueta etiqueta del estado; obligatorio.
     * @return el estado.
     * @throws ReglaNegocioException si la etiqueta falta o es desconocida (422).
     */
    static EstadoConsentimiento estadoConsentimientoRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado de consentimiento es obligatorio.");
        }
        try {
            return EstadoConsentimiento.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de consentimiento desconocido: " + etiqueta);
        }
    }

    /**
     * Interpreta una etiqueta como {@link EstadoConversacion}; opcional cuando se
     * admite ausencia de filtro.
     *
     * @param etiqueta etiqueta del estado; {@code null}/blanco devuelve {@code null}.
     * @return el estado, o {@code null} si no se indico.
     * @throws ReglaNegocioException si la etiqueta es desconocida (422).
     */
    static EstadoConversacion estadoConversacionOpcional(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        try {
            return EstadoConversacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Conversacion desconocido: " + etiqueta);
        }
    }
}
