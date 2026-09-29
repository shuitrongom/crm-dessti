package com.dessti.crm.operacion.proyecto.adapter.in.rest;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la decision de una evidencia (aprobar/rechazar). El motivo es
 * opcional en el contrato (se exige al RECHAZAR en la capa de dominio, donde se
 * valida con {@code ReglaNegocioException} 422 si falta). Al aprobar se ignora.
 *
 * @param motivo motivo del rechazo (obligatorio al rechazar; 1..500).
 */
public record DecisionEvidenciaRequest(
        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres.")
        String motivo) {
}
