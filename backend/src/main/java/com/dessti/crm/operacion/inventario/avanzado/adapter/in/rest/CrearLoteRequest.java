package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta un Lote de un Material (Req 60.4). DTO de entrada
 * del contrato REST, distinto de la entidad JPA. El Material viaja en la ruta. El codigo es
 * unico por Material dentro del tenant; un codigo duplicado se rechaza con 422.
 *
 * @param codigo           codigo del Lote; obligatorio, 1..100 caracteres.
 * @param fechaCaducidad   fecha de caducidad del Lote; opcional.
 * @param fechaFabricacion fecha de fabricacion/recepcion del Lote; opcional (V86).
 * @param notas            observaciones libres del Lote; opcional, <= 500 (V86).
 */
public record CrearLoteRequest(
        @NotBlank @Size(max = 100) String codigo,
        LocalDate fechaCaducidad,
        LocalDate fechaFabricacion,
        @Size(max = 500) String notas) {
}