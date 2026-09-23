package com.dessti.crm.rhnomina.empleado.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.rhnomina.empleado.domain.Incidencia;

/**
 * DTO de salida de una {@link Incidencia} (Req 12.2, 40.3), distinto de la
 * entidad de persistencia. El tipo se expone como su etiqueta ASCII persistida.
 *
 * @param id            identificador de la Incidencia.
 * @param empleadoId    Empleado al que pertenece.
 * @param periodoNomina codigo del Periodo_Nomina (AAAA-MM).
 * @param tipo          tipo de incidencia (etiqueta ASCII).
 * @param cantidad      cantidad asociada; puede ser {@code null}.
 * @param descripcion   nota opcional; puede ser {@code null}.
 * @param version       version para concurrencia optimista (Req 49).
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record IncidenciaDto(
        UUID id,
        UUID empleadoId,
        String periodoNomina,
        String tipo,
        BigDecimal cantidad,
        String descripcion,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Incidencia} a su DTO de salida.
     *
     * @param incidencia entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static IncidenciaDto de(Incidencia incidencia) {
        return new IncidenciaDto(
                incidencia.getId(),
                incidencia.getEmpleadoId(),
                incidencia.getPeriodoNomina(),
                incidencia.getTipo().valorBd(),
                incidencia.getCantidad(),
                incidencia.getDescripcion(),
                incidencia.getVersion(),
                incidencia.getCreatedAt(),
                incidencia.getUpdatedAt());
    }
}
