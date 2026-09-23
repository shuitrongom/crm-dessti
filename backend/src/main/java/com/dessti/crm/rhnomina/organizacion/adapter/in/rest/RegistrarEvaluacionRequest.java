package com.dessti.crm.rhnomina.organizacion.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar una Evaluacion_Desempeno (Req 61.3, 61.8,
 * tarea 36.1).
 *
 * <p>DTO de entrada del contrato REST, distinto de las entidades y del comando de
 * aplicacion (Req 12.2). El {@code tenant_id} NO se acepta en la peticion; se
 * deriva del contexto autenticado (Req 23.4).</p>
 *
 * <p><strong>Alcance de la validacion de campo:</strong> aqui se comprueba la
 * presencia y los limites amplios (Bean Validation -&gt; 400). La escala exacta
 * (1.00..5.00) y el formato del periodo los valida el dominio (que responde 422),
 * por lo que los limites de {@code @DecimalMin/@DecimalMax} coinciden con la escala
 * para dar un mensaje temprano coherente sin alterar el codigo de error del dominio.</p>
 *
 * @param empleadoId   Empleado evaluado; obligatorio.
 * @param periodo      codigo del periodo {@code AAAA-MM}; obligatorio.
 * @param calificacion calificacion dentro de la escala 1.00..5.00; obligatoria.
 * @param comentarios  comentarios opcionales (&lt;= 1000).
 */
public record RegistrarEvaluacionRequest(
        @NotNull UUID empleadoId,
        @NotBlank @Size(max = 7) String periodo,
        @NotNull @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal calificacion,
        @Size(max = 1000) String comentarios) {
}
