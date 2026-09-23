package com.dessti.crm.rhnomina.empleado.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Comando de alta de un {@link com.dessti.crm.rhnomina.empleado.domain.Empleado}
 * junto con su primer
 * {@link com.dessti.crm.rhnomina.empleado.domain.ContratoLaboral} (Req 40.1).
 * Objeto de entrada de la capa de aplicacion, distinto de las entidades. Ambos
 * agregados se crean atomicamente en una sola transaccion.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> este comando NO incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado y no se acepta
 * como parametro manipulable de la peticion.</p>
 *
 * @param nombre        nombre completo del Empleado; obligatorio (1..200).
 * @param rfc           RFC de persona fisica; obligatorio (13, formato valido).
 * @param curp          CURP; obligatoria (18, formato valido).
 * @param nss           NSS del IMSS; obligatorio (11 digitos).
 * @param fechaIngreso  fecha de ingreso; obligatoria.
 * @param tipoContrato  tipo de contrato (etiqueta ASCII); obligatorio.
 * @param salarioDiario salario diario; obligatorio y positivo.
 * @param periodicidad  periodicidad de pago (etiqueta ASCII); obligatoria.
 * @param fechaInicio   fecha de inicio del contrato; obligatoria.
 */
public record AltaEmpleadoCommand(
        String nombre,
        String rfc,
        String curp,
        String nss,
        LocalDate fechaIngreso,
        String tipoContrato,
        BigDecimal salarioDiario,
        String periodicidad,
        LocalDate fechaInicio) {
}
