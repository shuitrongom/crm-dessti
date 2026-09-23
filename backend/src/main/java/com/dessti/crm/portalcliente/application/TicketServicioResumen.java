package com.dessti.crm.portalcliente.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Resumen de un {@code Ticket_Servicio} tal como lo expone el Portal del Cliente.
 * Definido por el <strong>Nucleo</strong> (el Portal) e implementado por el
 * vertical de anuncios (parte de Mantenimiento) a traves del
 * {@link ResumenTicketsPort} (Req 10.5). Reproduce exactamente la forma del
 * {@code TicketServicioDto} del vertical (mismos nombres de campo) para preservar
 * el contrato REST del Portal frente al frontend (Req 10.4).
 *
 * @param id                      identificador del Ticket_Servicio (Req 20.2).
 * @param contratoMantenimientoId Contrato de SLA asociado, o {@code null} si es
 *                                manual (Req 20.2).
 * @param clienteId               Cliente del ticket (Req 20.7).
 * @param origen                  etiqueta del origen ({@code manual}/{@code preventivo}).
 * @param estado                  etiqueta del estado (Req 20.4).
 * @param asignadoTipo            tipo de asignacion, o {@code null} (Req 20.3).
 * @param asignadoId              referencia al destinatario, o {@code null}.
 * @param abiertoEn               instante UTC de apertura (Req 20.6).
 * @param resueltoEn              instante UTC de resolucion, o {@code null}.
 * @param slaRespuestaCumplido    cumplimiento del tiempo de respuesta, o {@code null}.
 * @param slaResolucionCumplido   cumplimiento del tiempo de resolucion, o {@code null}.
 * @param version                 version para concurrencia optimista (Req 49).
 * @param createdAt               instante de alta (UTC).
 * @param updatedAt               instante de la ultima modificacion (UTC).
 */
public record TicketServicioResumen(
        UUID id,
        UUID contratoMantenimientoId,
        UUID clienteId,
        String origen,
        String estado,
        String asignadoTipo,
        UUID asignadoId,
        Instant abiertoEn,
        Instant resueltoEn,
        Boolean slaRespuestaCumplido,
        Boolean slaResolucionCumplido,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
