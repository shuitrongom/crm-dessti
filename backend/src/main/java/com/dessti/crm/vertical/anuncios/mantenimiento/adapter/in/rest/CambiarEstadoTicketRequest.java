package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para cambiar el estado de un Ticket_Servicio (Req 20.4). DTO
 * de entrada del contrato REST. La etiqueta se valida y traduce a
 * {@link com.dessti.crm.vertical.anuncios.mantenimiento.domain.EstadoTicketServicio} en la capa de
 * aplicacion; una transicion no permitida se rechaza con 409 (Req 20.5).
 *
 * @param estado etiqueta del estado destino ({@code abierto}, {@code asignado},
 *               {@code en_proceso}, {@code resuelto}, {@code cerrado}); obligatoria.
 */
public record CambiarEstadoTicketRequest(@NotBlank String estado) {
}
