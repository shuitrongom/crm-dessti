package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para dar seguimiento a una alerta de stock (Req 60): marcarla como
 * atendida o no atendida. DTO de entrada del contrato REST. La alerta viaja en la ruta.
 *
 * @param atendida nuevo estado de seguimiento; obligatorio.
 */
public record ActualizarAlertaRequest(
        @NotNull Boolean atendida) {
}
