package com.dessti.crm.rhnomina.organizacion.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.rhnomina.organizacion.domain.Puesto;

/**
 * DTO de salida de un {@link Puesto} (Req 12.2, 61.1), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 *
 * @param id               identificador del Puesto.
 * @param nombre           nombre del Puesto.
 * @param descripcion      descripcion; puede ser {@code null}.
 * @param puestoSuperiorId superior directo en la jerarquia; {@code null} si es raiz.
 * @param activo           {@code true} si el Puesto esta vigente.
 * @param version          version para concurrencia optimista (Req 49).
 * @param createdAt        instante de alta (UTC).
 * @param updatedAt        instante de la ultima modificacion (UTC).
 */
public record PuestoDto(
        UUID id,
        String nombre,
        String descripcion,
        UUID puestoSuperiorId,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Puesto} a su DTO de salida.
     *
     * @param puesto entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PuestoDto de(Puesto puesto) {
        return new PuestoDto(
                puesto.getId(),
                puesto.getNombre(),
                puesto.getDescripcion(),
                puesto.getPuestoSuperiorId(),
                puesto.isActivo(),
                puesto.getVersion(),
                puesto.getCreatedAt(),
                puesto.getUpdatedAt());
    }
}
