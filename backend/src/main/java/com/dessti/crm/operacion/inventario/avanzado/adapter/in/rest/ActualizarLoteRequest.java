package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import java.time.LocalDate;

/**
 * Cuerpo de la peticion para actualizar un Lote (Req 60). DTO de entrada del contrato
 * REST, distinto de la entidad JPA. El Lote viaja en la ruta. Solo la fecha de caducidad
 * es editable: el codigo del Lote es su identidad de negocio (unico por Material) y no se
 * modifica.
 *
 * @param fechaCaducidad nueva fecha de caducidad del Lote; {@code null} = sin caducidad.
 */
public record ActualizarLoteRequest(
        LocalDate fechaCaducidad) {
}
