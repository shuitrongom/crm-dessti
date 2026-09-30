package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para actualizar un Lote (Req 60). DTO de entrada del contrato
 * REST, distinto de la entidad JPA. El Lote viaja en la ruta. Son editables la caducidad,
 * la fecha de fabricacion y las notas; el codigo del Lote es su identidad de negocio
 * (unico por Material) y NO se modifica.
 *
 * @param fechaCaducidad   nueva fecha de caducidad del Lote; {@code null} = sin caducidad.
 * @param fechaFabricacion nueva fecha de fabricacion/recepcion; {@code null} = sin dato (V86).
 * @param notas            nuevas observaciones libres; {@code null}/vacio = sin notas (V86).
 */
public record ActualizarLoteRequest(
        LocalDate fechaCaducidad,
        LocalDate fechaFabricacion,
        @Size(max = 500) String notas) {
}
