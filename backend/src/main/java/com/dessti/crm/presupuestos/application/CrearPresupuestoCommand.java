package com.dessti.crm.presupuestos.application;

import java.math.BigDecimal;

/**
 * Comando de aplicacion para crear un Presupuesto por area y periodo con montos
 * estimados de ingresos y/o egresos (Req 62.1). Objeto de entrada inmutable de la
 * capa de aplicacion, distinto de la entidad JPA y del DTO de contrato REST.
 *
 * @param area              area funcional del presupuesto (por ejemplo
 *                          {@code comercial}, {@code produccion}, {@code compras},
 *                          {@code nomina}); obligatoria.
 * @param periodo           periodo 'AAAA-MM' o codigo equivalente; obligatorio.
 * @param ingresosEstimados ingresos estimados; no negativo (0 si no se presupuestan).
 * @param egresosEstimados  egresos estimados; no negativo (0 si no se presupuestan).
 */
public record CrearPresupuestoCommand(
        String area,
        String periodo,
        BigDecimal ingresosEstimados,
        BigDecimal egresosEstimados) {
}
