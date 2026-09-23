package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para generar un Ticket_Servicio (Req 20.2). DTO de entrada
 * del contrato REST, distinto de la entidad JPA. El contrato es opcional (un ticket
 * manual puede no tenerlo); la etiqueta de origen se valida y traduce a
 * {@link com.dessti.crm.vertical.anuncios.mantenimiento.domain.OrigenTicket} en la capa de aplicacion.
 *
 * @param contratoMantenimientoId Contrato de SLA asociado; opcional ({@code null} en
 *                                tickets manuales sin contrato).
 * @param clienteId               Cliente del ticket; obligatorio.
 * @param origen                  etiqueta del origen ({@code manual}/{@code preventivo});
 *                                obligatoria.
 */
public record GenerarTicketRequest(
        UUID contratoMantenimientoId,
        @NotNull UUID clienteId,
        @NotBlank String origen) {
}
