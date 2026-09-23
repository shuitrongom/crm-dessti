package com.dessti.crm.rhnomina.organizacion.adapter.in.rest;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para asignar un Empleado a un Puesto (Req 61.2, tarea 36.1).
 *
 * <p>DTO de entrada del contrato REST, distinto de las entidades y del comando de
 * aplicacion (Req 12.2). El {@code tenant_id} NO se acepta en la peticion; se
 * deriva del contexto autenticado (Req 23.4).</p>
 *
 * @param empleadoId  Empleado a asignar; obligatorio.
 * @param puestoId    Puesto destino; obligatorio.
 * @param fechaInicio fecha de inicio de la asignacion; obligatoria.
 */
public record AsignarEmpleadoRequest(
        @NotNull UUID empleadoId,
        @NotNull UUID puestoId,
        @NotNull LocalDate fechaInicio) {
}
