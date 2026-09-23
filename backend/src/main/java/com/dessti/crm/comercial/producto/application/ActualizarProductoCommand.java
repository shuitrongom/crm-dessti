package com.dessti.crm.comercial.producto.application;

/**
 * Comando de actualizacion de un
 * {@link com.dessti.crm.comercial.producto.domain.Producto} (Req 59). Distinto
 * de la entidad; no incluye el {@code tenant_id} (Req 23.4).
 *
 * @param nombre       nuevo nombre; obligatorio (1..200).
 * @param unidad       nueva unidad; obligatoria.
 * @param descripcion  nueva descripcion; obligatoria.
 * @param clienteMeta  cliente meta; opcional (Req 59.5).
 * @param alianzas     alianzas; opcional (Req 59.5).
 * @param competencia  competencia; opcional (Req 59.5).
 * @param foto         foto/imagen (URL o {@code data URI}); opcional.
 *                     {@code null}/blanco la limpia (Req 59).
 */
public record ActualizarProductoCommand(
        String nombre,
        String unidad,
        String descripcion,
        String clienteMeta,
        String alianzas,
        String competencia,
        String foto) {
}
