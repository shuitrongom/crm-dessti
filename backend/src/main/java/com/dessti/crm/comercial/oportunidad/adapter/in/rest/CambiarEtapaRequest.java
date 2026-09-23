package com.dessti.crm.comercial.oportunidad.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para cambiar la etapa de una Oportunidad (Req 14.3). DTO
 * de entrada del contrato REST. La etiqueta se valida y traduce a
 * {@link com.dessti.crm.comercial.oportunidad.domain.EtapaOportunidad} en la
 * capa de aplicacion; una transicion no permitida se rechaza con 409.
 *
 * @param etapa etiqueta de la etapa destino ({@code nuevo}, {@code calificado},
 *              {@code propuesta}, {@code negociacion}, {@code ganado},
 *              {@code perdido}); obligatoria.
 */
public record CambiarEtapaRequest(@NotBlank String etapa) {
}
