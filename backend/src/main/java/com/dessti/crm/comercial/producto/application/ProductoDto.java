package com.dessti.crm.comercial.producto.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.comercial.producto.domain.Producto;

/**
 * DTO de salida de un {@link Producto} (Req 12.2, 59), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 *
 * @param id           identificador del Producto.
 * @param nombre       nombre del Producto.
 * @param unidad       unidad de medida/venta.
 * @param descripcion  descripcion del Producto.
 * @param clienteMeta  cliente meta; puede ser {@code null} (Req 59.5).
 * @param alianzas     alianzas; puede ser {@code null} (Req 59.5).
 * @param competencia  competencia; puede ser {@code null} (Req 59.5).
 * @param foto         foto/imagen del Producto (URL o {@code data URI}); puede
 *                     ser {@code null} (Req 59).
 * @param activo       {@code true} si el Producto esta vigente (no dado de baja).
 * @param version      version para concurrencia optimista (Req 49).
 * @param createdAt    instante de alta (UTC).
 * @param updatedAt    instante de la ultima modificacion (UTC).
 *
 * <h2>Decision sobre {@code foto} en el listado (Req 59.7)</h2>
 * <p>Se incluye {@code foto} en este unico DTO y se reutiliza tanto en el GET
 * puntual ({@code GET /productos/{id}}) como en la proyeccion del listado
 * paginado. El catalogo de Productos es modesto y las imagenes se esperan
 * pequenas (URL o {@code data URI} acotado a ~1 MiB), por lo que se opta por el
 * enfoque mas simple y correcto (un solo DTO) en vez de un DTO de listado
 * ligero separado; asi el listado puede mostrar la miniatura y el detalle sirve
 * la vista/edicion sin una segunda llamada.</p>
 */
public record ProductoDto(
        UUID id,
        String nombre,
        String unidad,
        String descripcion,
        String clienteMeta,
        String alianzas,
        String competencia,
        String foto,
        boolean activo,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Producto} a su DTO de salida.
     *
     * @param producto entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ProductoDto de(Producto producto) {
        return new ProductoDto(
                producto.getId(),
                producto.getNombre(),
                producto.getUnidad(),
                producto.getDescripcion(),
                producto.getClienteMeta(),
                producto.getAlianzas(),
                producto.getCompetencia(),
                producto.getFoto(),
                producto.isActivo(),
                producto.getVersion(),
                producto.getCreatedAt(),
                producto.getUpdatedAt());
    }
}
