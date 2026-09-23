package com.dessti.crm.compras.proveedor.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.compras.proveedor.domain.Proveedor;

/**
 * DTO de salida de un {@link Proveedor} (Req 12.2, 29), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 *
 * @param id        identificador del Proveedor.
 * @param nombre    razon social o nombre.
 * @param rfc       identificador fiscal (normalizado a mayusculas).
 * @param email     correo electronico; puede ser {@code null}.
 * @param telefono  telefono; puede ser {@code null}.
 * @param activo    {@code true} si el Proveedor esta vigente (no dado de baja).
 * @param version   version para concurrencia optimista (Req 49).
 * @param createdAt instante de alta (UTC).
 * @param updatedAt instante de la ultima modificacion (UTC).
 */
public record ProveedorDto(
        UUID id,
        String nombre,
        String rfc,
        String email,
        String telefono,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Proveedor} a su DTO de salida.
     *
     * @param proveedor entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ProveedorDto de(Proveedor proveedor) {
        return new ProveedorDto(
                proveedor.getId(),
                proveedor.getNombre(),
                proveedor.getRfc(),
                proveedor.getEmail(),
                proveedor.getTelefono(),
                proveedor.isActivo(),
                proveedor.getVersion(),
                proveedor.getCreatedAt(),
                proveedor.getUpdatedAt());
    }
}
