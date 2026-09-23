package com.dessti.crm.rhnomina.empleado.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.rhnomina.empleado.domain.Empleado;

/**
 * DTO de salida de un {@link Empleado} (Req 12.2, 40), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 *
 * @param id           identificador del Empleado.
 * @param nombre       nombre completo.
 * @param rfc          RFC (normalizado a mayusculas).
 * @param curp         CURP (normalizada a mayusculas).
 * @param nss          NSS del IMSS (11 digitos).
 * @param fechaIngreso fecha de ingreso.
 * @param activo       {@code true} si el Empleado esta vigente (no dado de baja).
 * @param version      version para concurrencia optimista (Req 49).
 * @param createdAt    instante de alta (UTC).
 * @param updatedAt    instante de la ultima modificacion (UTC).
 */
public record EmpleadoDto(
        UUID id,
        String nombre,
        String rfc,
        String curp,
        String nss,
        LocalDate fechaIngreso,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Empleado} a su DTO de salida.
     *
     * @param empleado entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static EmpleadoDto de(Empleado empleado) {
        return new EmpleadoDto(
                empleado.getId(),
                empleado.getNombre(),
                empleado.getRfc(),
                empleado.getCurp(),
                empleado.getNss(),
                empleado.getFechaIngreso(),
                empleado.isActivo(),
                empleado.getVersion(),
                empleado.getCreatedAt(),
                empleado.getUpdatedAt());
    }
}
