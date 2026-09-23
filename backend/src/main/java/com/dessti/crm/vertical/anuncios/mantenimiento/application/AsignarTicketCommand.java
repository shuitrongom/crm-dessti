package com.dessti.crm.vertical.anuncios.mantenimiento.application;

import java.util.UUID;

/**
 * Comando de aplicacion para asignar un Ticket_Servicio a un tecnico o a una
 * Cuadrilla (Req 20.3). El tipo viaja como etiqueta de negocio ({@code tecnico}/
 * {@code cuadrilla}) que la capa de aplicacion interpreta.
 *
 * @param asignadoTipo etiqueta del tipo de destinatario ({@code tecnico}/
 *                     {@code cuadrilla}); obligatorio.
 * @param asignadoId   referencia (debil) al destinatario; obligatorio.
 */
public record AsignarTicketCommand(
        String asignadoTipo,
        UUID asignadoId) {
}
