package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de la peticion para registrar un Contrato_Mantenimiento (Req 20.1). DTO de
 * entrada del contrato REST, distinto de la entidad JPA. La etiqueta de tipo se
 * valida y traduce a
 * {@link com.dessti.crm.vertical.anuncios.mantenimiento.domain.TipoContratoMantenimiento} en la capa
 * de aplicacion.
 *
 * @param clienteId          Cliente al que se asocia el contrato; obligatorio.
 * @param tipo               etiqueta del tipo ({@code preventivo}/{@code correctivo});
 *                           obligatoria.
 * @param slaRespuestaHoras  tiempo de respuesta del SLA en horas; obligatorio y &gt; 0.
 * @param slaResolucionHoras tiempo de resolucion del SLA en horas; obligatorio y &gt; 0.
 */
public record CrearContratoRequest(
        @NotNull UUID clienteId,
        @NotBlank String tipo,
        @NotNull @Positive Integer slaRespuestaHoras,
        @NotNull @Positive Integer slaResolucionHoras) {
}
