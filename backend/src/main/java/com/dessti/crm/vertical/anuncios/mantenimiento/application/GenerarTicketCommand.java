package com.dessti.crm.vertical.anuncios.mantenimiento.application;

import java.util.UUID;

/**
 * Comando de aplicacion para generar un Ticket_Servicio (Req 20.2). Un ticket
 * puede ser manual (sin contrato) o generado por mantenimiento preventivo (con
 * contrato). El origen viaja como etiqueta de negocio ({@code manual}/
 * {@code preventivo}) que la capa de aplicacion interpreta.
 *
 * @param contratoMantenimientoId Contrato de SLA asociado; opcional ({@code null}
 *                                en tickets manuales sin contrato).
 * @param clienteId               Cliente del ticket (para el filtro del Req 20.7);
 *                                obligatorio.
 * @param origen                  etiqueta del origen ({@code manual}/{@code preventivo}).
 */
public record GenerarTicketCommand(
        UUID contratoMantenimientoId,
        UUID clienteId,
        String origen) {
}
