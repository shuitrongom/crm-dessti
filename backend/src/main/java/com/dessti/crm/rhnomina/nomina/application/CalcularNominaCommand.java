package com.dessti.crm.rhnomina.nomina.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de aplicacion para calcular una Nomina (Req 41.1, 41.2). Ademas del
 * identificador de la Nomina, transporta los parametros del proceso que se aplican
 * a los Empleados del periodo: aguinaldo, PTU y tasa de descuento de Infonavit.
 * Todos son opcionales ({@code null} equivale a cero / sin credito).
 *
 * <p>El {@code tenant_id} y el actor se derivan del contexto de seguridad (Req 23.4);
 * no viajan en el comando.</p>
 *
 * @param nominaId      identificador de la Nomina a calcular; obligatorio.
 * @param aguinaldo     importe de aguinaldo a considerar por Empleado; {@code null}
 *                      equivale a {@code 0} (no aplica en este periodo).
 * @param ptu           importe de PTU a considerar por Empleado; {@code null}
 *                      equivale a {@code 0} (no aplica en este periodo).
 * @param tasaInfonavit tasa de descuento de Infonavit en {@code [0, 1]}; {@code null}
 *                      equivale a {@code 0} (el Empleado no tiene credito Infonavit).
 */
public record CalcularNominaCommand(
        UUID nominaId,
        BigDecimal aguinaldo,
        BigDecimal ptu,
        BigDecimal tasaInfonavit) {
}
