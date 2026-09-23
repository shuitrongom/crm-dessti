package com.dessti.crm.operacion.inventario.avanzado.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.operacion.inventario.avanzado.domain.Almacen;

/**
 * DTO de salida de un {@link Almacen} (Req 12.2, 60), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 *
 * @param id        identificador del Almacen.
 * @param nombre    nombre del Almacen.
 * @param tipo      tipo del Almacen ({@code sucursal}/{@code bodega}).
 * @param activo    {@code true} si el Almacen esta activo (no dado de baja).
 * @param version   version para concurrencia optimista (Req 49).
 * @param createdAt instante de alta (UTC).
 * @param updatedAt instante de la ultima modificacion (UTC).
 */
public record AlmacenDto(
        UUID id,
        String nombre,
        String tipo,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Almacen} a su DTO de salida.
     *
     * @param almacen entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static AlmacenDto de(Almacen almacen) {
        return new AlmacenDto(
                almacen.getId(),
                almacen.getNombre(),
                almacen.getTipo(),
                almacen.isActivo(),
                almacen.getVersion(),
                almacen.getCreatedAt(),
                almacen.getUpdatedAt());
    }
}
