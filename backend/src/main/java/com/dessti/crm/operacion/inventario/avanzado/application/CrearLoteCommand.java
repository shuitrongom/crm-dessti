package com.dessti.crm.operacion.inventario.avanzado.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando de aplicacion para dar de alta un Lote de un Material (Req 60, tarea 23.2).
 * Transporta los datos de entrada del caso de uso, desacoplados del contrato REST y de la
 * entidad JPA. La validacion de formato (codigo 1..100, unicidad por Material dentro del
 * tenant) la refuerzan el dominio ({@code Lote.crear}) y la restriccion {@code UNIQUE} de V26.
 *
 * @param materialId     Material del Lote; obligatorio.
 * @param codigo         codigo del Lote; obligatorio, 1..100 caracteres.
 * @param fechaCaducidad fecha de caducidad; opcional ({@code null} = sin caducidad).
 */
public record CrearLoteCommand(
        UUID materialId,
        String codigo,
        LocalDate fechaCaducidad) {
}
