package com.dessti.crm.rhnomina.nomina.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear una Nomina de un Periodo_Nomina (Req 41.1). DTO de
 * entrada del contrato REST, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.rhnomina.nomina.application.CrearNominaCommand} (Req 12.2).
 * El <em>formato</em> del Periodo_Nomina lo valida la capa de dominio (422); aqui solo
 * se comprueba presencia y longitud maxima.
 *
 * @param periodoNomina codigo del Periodo_Nomina (AAAA-MM); obligatorio.
 */
public record CrearNominaRequest(
        @NotBlank @Size(max = 7) String periodoNomina) {
}
