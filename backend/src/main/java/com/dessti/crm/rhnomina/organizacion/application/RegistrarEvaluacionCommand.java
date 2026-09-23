package com.dessti.crm.rhnomina.organizacion.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de registro de una
 * {@link com.dessti.crm.rhnomina.organizacion.domain.EvaluacionDesempeno}
 * (Req 61.3, 61.8). Objeto de entrada de la capa de aplicacion, distinto de las
 * entidades.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el {@code tenant_id};
 * el tenant se deriva del contexto autenticado.</p>
 *
 * @param empleadoId   Empleado evaluado; obligatorio.
 * @param periodo      codigo del periodo {@code AAAA-MM}; obligatorio.
 * @param calificacion calificacion dentro de la escala 1.00..5.00; obligatoria.
 * @param comentarios  comentarios opcionales (&lt;= 1000).
 */
public record RegistrarEvaluacionCommand(
        UUID empleadoId,
        String periodo,
        BigDecimal calificacion,
        String comentarios) {
}
