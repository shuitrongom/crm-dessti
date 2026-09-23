package com.dessti.crm.rhnomina.nomina.domain;

import java.math.BigDecimal;

/**
 * Entrada inmutable y ya validada del calculo de nomina de un Empleado en un
 * Periodo_Nomina (Req 41.1), que consume {@link CalculoNomina#calcular(EntradaNomina)}.
 *
 * <p>Es un objeto de dominio <strong>puro</strong> (sin Spring/JPA): la capa de
 * aplicacion ({@code ServicioNomina}) lo ensambla a partir del Contrato_Laboral
 * (salario diario y periodicidad -&gt; dias del periodo), de las Incidencia del
 * periodo (tiempo extra) y de los parametros del proceso (aguinaldo, PTU, tasa de
 * Infonavit). Al ser el motor una funcion pura, esta entrada asume valores ya
 * presentes y no negativos; la validacion de la <em>presencia</em> de los datos
 * fiscales del Empleado (Req 41.3) la realiza la aplicacion antes de construir esta
 * entrada.</p>
 *
 * @param salarioDiario salario diario del Contrato_Laboral; obligatorio y positivo.
 * @param diasPeriodo   numero de dias del Periodo_Nomina segun la periodicidad
 *                      (semanal=7, quincenal=15, mensual=30); obligatorio y positivo.
 * @param tiempoExtra   importe de tiempo extra del periodo (suma de incidencias);
 *                      no negativo ({@code 0} si no hay).
 * @param aguinaldo     importe de aguinaldo cuando aplica; no negativo ({@code 0} si no).
 * @param ptu           importe de PTU cuando aplica; no negativo ({@code 0} si no).
 * @param tasaInfonavit tasa de descuento de Infonavit en {@code [0, 1]} ({@code 0}
 *                      si el Empleado no tiene credito).
 */
public record EntradaNomina(
        BigDecimal salarioDiario,
        int diasPeriodo,
        BigDecimal tiempoExtra,
        BigDecimal aguinaldo,
        BigDecimal ptu,
        BigDecimal tasaInfonavit) {
}
