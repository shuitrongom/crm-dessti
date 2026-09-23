package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para asignar un Ticket_Servicio a un tecnico o a una
 * Cuadrilla (Req 20.3). DTO de entrada del contrato REST. La etiqueta de tipo se
 * valida y traduce a {@link com.dessti.crm.vertical.anuncios.mantenimiento.domain.AsignadoTipo} en la
 * capa de aplicacion.
 *
 * @param asignadoTipo etiqueta del tipo de destinatario ({@code tecnico}/
 *                     {@code cuadrilla}); obligatoria.
 * @param asignadoId   referencia (debil) al destinatario; obligatorio.
 */
public record AsignarTicketRequest(
        @NotBlank String asignadoTipo,
        @NotNull UUID asignadoId) {
}
