package com.dessti.crm.comercial.producto.application;

/**
 * Comando de creacion de un {@link com.dessti.crm.comercial.producto.domain.Producto}
 * (Req 59.1). Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param nombre       nombre del Producto; obligatorio (1..200).
 * @param unidad       unidad de medida/venta; obligatoria.
 * @param descripcion  descripcion; obligatoria.
 * @param clienteMeta  cliente meta; opcional (Req 59.5).
 * @param alianzas     alianzas; opcional (Req 59.5).
 * @param competencia  competencia; opcional (Req 59.5).
 * @param foto         foto/imagen (URL o {@code data URI}); opcional (Req 59).
 */
public record CrearProductoCommand(
        String nombre,
        String unidad,
        String descripcion,
        String clienteMeta,
        String alianzas,
        String competencia,
        String foto) {
}
