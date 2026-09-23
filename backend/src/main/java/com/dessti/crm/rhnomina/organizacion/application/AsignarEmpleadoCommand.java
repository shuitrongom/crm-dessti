package com.dessti.crm.rhnomina.organizacion.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando de asignacion de un Empleado a un
 * {@link com.dessti.crm.rhnomina.organizacion.domain.Puesto} (Req 61.2). Objeto
 * de entrada de la capa de aplicacion, distinto de las entidades.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el {@code tenant_id};
 * el tenant se deriva del contexto autenticado.</p>
 *
 * @param empleadoId  Empleado a asignar; obligatorio.
 * @param puestoId    Puesto destino; obligatorio.
 * @param fechaInicio fecha de inicio de la asignacion; obligatoria.
 */
public record AsignarEmpleadoCommand(
        UUID empleadoId,
        UUID puestoId,
        LocalDate fechaInicio) {
}
