package com.dessti.crm.rhnomina.empleado.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.rhnomina.empleado.domain.ContratoLaboral;

/**
 * DTO de salida de un {@link ContratoLaboral} (Req 12.2, 40.1), distinto de la
 * entidad de persistencia. El tipo y la periodicidad se exponen como su etiqueta
 * ASCII persistida.
 *
 * @param id            identificador del Contrato_Laboral.
 * @param empleadoId    Empleado titular.
 * @param tipo          tipo de contrato (etiqueta ASCII).
 * @param salarioDiario salario diario.
 * @param periodicidad  periodicidad de pago (etiqueta ASCII).
 * @param fechaInicio   fecha de inicio.
 * @param fechaFin      fecha de fin; puede ser {@code null}.
 * @param activo        {@code true} si el contrato esta vigente.
 * @param version       version para concurrencia optimista (Req 49).
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record ContratoLaboralDto(
        UUID id,
        UUID empleadoId,
        String tipo,
        BigDecimal salarioDiario,
        String periodicidad,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ContratoLaboral} a su DTO de salida.
     *
     * @param contrato entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ContratoLaboralDto de(ContratoLaboral contrato) {
        return new ContratoLaboralDto(
                contrato.getId(),
                contrato.getEmpleadoId(),
                contrato.getTipo().valorBd(),
                contrato.getSalarioDiario(),
                contrato.getPeriodicidad().valorBd(),
                contrato.getFechaInicio(),
                contrato.getFechaFin(),
                contrato.isActivo(),
                contrato.getVersion(),
                contrato.getCreatedAt(),
                contrato.getUpdatedAt());
    }
}
