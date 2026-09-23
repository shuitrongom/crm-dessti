package com.dessti.crm.comercial.cliente.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.comercial.cliente.domain.Contacto;

/**
 * DTO de salida de un {@link Contacto} (Req 12.2, 5.5), distinto de la entidad
 * de persistencia.
 *
 * @param id        identificador del Contacto.
 * @param clienteId identificador del Cliente propietario.
 * @param nombre    nombre del Contacto.
 * @param email     correo electronico; puede ser {@code null}.
 * @param telefono  telefono; puede ser {@code null}.
 * @param activo    {@code true} si el Contacto esta vigente.
 * @param version   version para concurrencia optimista (Req 49).
 * @param createdAt instante de alta (UTC).
 * @param updatedAt instante de la ultima modificacion (UTC).
 */
public record ContactoDto(
        UUID id,
        UUID clienteId,
        String nombre,
        String email,
        String telefono,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Contacto} a su DTO de salida.
     *
     * @param contacto entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ContactoDto de(Contacto contacto) {
        return new ContactoDto(
                contacto.getId(),
                contacto.getClienteId(),
                contacto.getNombre(),
                contacto.getEmail(),
                contacto.getTelefono(),
                contacto.isActivo(),
                contacto.getVersion(),
                contacto.getCreatedAt(),
                contacto.getUpdatedAt());
    }
}
