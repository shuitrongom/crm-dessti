package com.dessti.crm.rhnomina.organizacion.adapter.in.rest;

import java.util.UUID;

/**
 * Cuerpo de la peticion para mover un Puesto en la jerarquia cambiando su superior
 * directo (Req 61.1, 61.7, tarea 36.1).
 *
 * <p>DTO de entrada del contrato REST, distinto de las entidades. Un
 * {@code nuevoSuperiorId} nulo deja el Puesto como raiz del organigrama. El
 * identificador del Puesto a mover se toma de la ruta.</p>
 *
 * @param nuevoSuperiorId nuevo superior directo; {@code null} para dejarlo raiz.
 */
public record MoverPuestoRequest(UUID nuevoSuperiorId) {
}
