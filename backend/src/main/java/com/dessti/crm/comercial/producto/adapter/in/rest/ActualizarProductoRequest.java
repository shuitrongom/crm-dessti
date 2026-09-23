package com.dessti.crm.comercial.producto.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para actualizar un Producto (Req 59). DTO de entrada del
 * contrato REST, distinto de la entidad y del comando
 * {@link com.dessti.crm.comercial.producto.application.ActualizarProductoCommand}
 * (Req 12.2). El {@code tenant_id} se deriva del contexto (Req 23.4).
 *
 * @param nombre       nuevo nombre; obligatorio (1..200).
 * @param unidad       nueva unidad; obligatoria.
 * @param descripcion  nueva descripcion; obligatoria.
 * @param clienteMeta  cliente meta; opcional (Req 59.5).
 * @param alianzas     alianzas; opcional (Req 59.5).
 * @param competencia  competencia; opcional (Req 59.5).
 * @param foto         foto/imagen del Producto como URL o {@code data URI}
 *                     (base64); opcional. {@code null}/blanco la limpia. La
 *                     columna es {@code TEXT}; la cota definitiva la aplica el
 *                     dominio (422 si excede), la anotacion {@link Size} ofrece
 *                     una validacion temprana alineada (~1 MiB) (Req 59).
 */
public record ActualizarProductoRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(max = 50) String unidad,
        @NotBlank @Size(max = 2000) String descripcion,
        String clienteMeta,
        String alianzas,
        String competencia,
        @Size(max = 1_048_576) String foto) {
}
