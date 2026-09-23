package com.dessti.crm.rhnomina.empleado.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta un Empleado junto con su primer
 * Contrato_Laboral (Req 40.1, tarea 34.1).
 *
 * <p>DTO de entrada del contrato REST, <strong>distinto</strong> de las entidades
 * de persistencia y del comando de aplicacion
 * {@link com.dessti.crm.rhnomina.empleado.application.AltaEmpleadoCommand}
 * (Req 12.2). El controlador lo traduce al comando antes de invocar el servicio.</p>
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> el {@code tenant_id} NO se
 * acepta en la peticion; se deriva del contexto autenticado.</p>
 *
 * <p><strong>Alcance de la validacion de campo (Req 40.2, 8):</strong> aqui solo
 * se comprueba la <em>presencia</em> y unos limites de longitud amplios
 * (Bean Validation -&gt; 400). El <em>formato</em> del RFC, la CURP y el NSS lo
 * valida el dominio en la capa de aplicacion (que responde 422), por lo que no se
 * duplica aqui una expresion regular estricta que alteraria el codigo de error
 * esperado.</p>
 *
 * @param nombre        nombre completo; obligatorio (1..200).
 * @param rfc           RFC de persona fisica; obligatorio (13).
 * @param curp          CURP; obligatoria (18).
 * @param nss           NSS del IMSS; obligatorio (11).
 * @param fechaIngreso  fecha de ingreso; obligatoria.
 * @param tipoContrato  tipo de contrato (etiqueta ASCII); obligatorio.
 * @param salarioDiario salario diario; obligatorio y positivo.
 * @param periodicidad  periodicidad de pago (etiqueta ASCII); obligatoria.
 * @param fechaInicio   fecha de inicio del contrato; obligatoria.
 */
public record AltaEmpleadoRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(min = 13, max = 13) String rfc,
        @NotBlank @Size(min = 18, max = 18) String curp,
        @NotBlank @Size(min = 11, max = 11) String nss,
        @NotNull LocalDate fechaIngreso,
        @NotBlank @Size(max = 20) String tipoContrato,
        @NotNull @Positive BigDecimal salarioDiario,
        @NotBlank @Size(max = 12) String periodicidad,
        @NotNull LocalDate fechaInicio) {
}
