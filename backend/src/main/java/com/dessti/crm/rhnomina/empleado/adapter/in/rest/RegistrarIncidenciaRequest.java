package com.dessti.crm.rhnomina.empleado.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar una Incidencia de un Empleado en un
 * Periodo_Nomina (Req 40.3, tarea 34.1). El identificador del Empleado se toma de
 * la ruta, no del cuerpo.
 *
 * <p>DTO de entrada del contrato REST, distinto de la entidad y del comando de
 * aplicacion {@link com.dessti.crm.rhnomina.empleado.application.RegistrarIncidenciaCommand}
 * (Req 12.2). El <em>formato</em> del Periodo_Nomina y el dominio del tipo los
 * valida la capa de aplicacion/dominio (422); aqui solo se comprueba presencia y
 * limites amplios.</p>
 *
 * @param periodoNomina codigo del Periodo_Nomina (AAAA-MM); obligatorio.
 * @param tipo          tipo de incidencia (etiqueta ASCII); obligatorio.
 * @param cantidad      cantidad asociada (horas/dias); opcional, no negativa.
 * @param descripcion   nota opcional (<= 500 caracteres).
 */
public record RegistrarIncidenciaRequest(
        @NotBlank @Size(max = 7) String periodoNomina,
        @NotBlank @Size(max = 14) String tipo,
        @PositiveOrZero BigDecimal cantidad,
        @Size(max = 500) String descripcion) {
}
