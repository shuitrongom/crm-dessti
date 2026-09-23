package com.dessti.crm.rhnomina.organizacion.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.rhnomina.organizacion.domain.AsignacionPuesto;

/**
 * DTO de salida de una {@link AsignacionPuesto} (Req 12.2, 61.2), distinto de la
 * entidad de persistencia.
 *
 * @param id          identificador de la asignacion.
 * @param empleadoId  Empleado asignado.
 * @param puestoId    Puesto destino.
 * @param fechaInicio fecha de inicio de la asignacion.
 * @param fechaFin    fecha de fin; {@code null} mientras esta vigente.
 * @param activa      {@code true} si la asignacion esta vigente.
 * @param version     version para concurrencia optimista (Req 49).
 * @param createdAt   instante de alta (UTC).
 * @param updatedAt   instante de la ultima modificacion (UTC).
 */
public record AsignacionPuestoDto(
        UUID id,
        UUID empleadoId,
        UUID puestoId,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        boolean activa,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link AsignacionPuesto} a su DTO de salida.
     *
     * @param asignacion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static AsignacionPuestoDto de(AsignacionPuesto asignacion) {
        return new AsignacionPuestoDto(
                asignacion.getId(),
                asignacion.getEmpleadoId(),
                asignacion.getPuestoId(),
                asignacion.getFechaInicio(),
                asignacion.getFechaFin(),
                asignacion.isActiva(),
                asignacion.getVersion(),
                asignacion.getCreatedAt(),
                asignacion.getUpdatedAt());
    }
}
