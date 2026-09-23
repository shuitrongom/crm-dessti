package com.dessti.crm.estrategia.adapter.in.rest;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar o actualizar (upsert) la mision, vision y
 * valores de la Empresa (Req 58.1). DTO de entrada del contrato REST, distinto de
 * la entidad JPA. Los tres campos son <strong>opcionales</strong> (la Empresa los
 * completa progresivamente); solo se acota su longitud maxima.
 *
 * @param mision  mision; opcional (hasta 5000 caracteres).
 * @param vision  vision; opcional (hasta 5000 caracteres).
 * @param valores valores; opcional (hasta 5000 caracteres).
 */
public record GuardarEsenciaRequest(
        @Size(max = 5000) String mision,
        @Size(max = 5000) String vision,
        @Size(max = 5000) String valores) {
}
