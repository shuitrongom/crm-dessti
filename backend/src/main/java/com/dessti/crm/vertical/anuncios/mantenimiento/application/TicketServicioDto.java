package com.dessti.crm.vertical.anuncios.mantenimiento.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.mantenimiento.domain.TicketServicio;

/**
 * DTO de salida de un {@link TicketServicio} (Req 20.2–20.6), distinto de la
 * entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA. El estado y el origen se exponen como sus etiquetas de negocio.
 *
 * @param id                     identificador del Ticket_Servicio (Req 20.2).
 * @param contratoMantenimientoId Contrato de SLA asociado, o {@code null} si es un
 *                               ticket manual sin contrato (Req 20.2).
 * @param clienteId              Cliente del ticket (para el filtro del Req 20.7).
 * @param origen                 etiqueta del origen ({@code manual}/{@code preventivo}).
 * @param estado                 etiqueta del estado (Req 20.4).
 * @param asignadoTipo           tipo de asignacion ({@code tecnico}/{@code cuadrilla}),
 *                               o {@code null} si no esta asignado (Req 20.3).
 * @param asignadoId             referencia (debil) al destinatario, o {@code null}.
 * @param abiertoEn              instante UTC de apertura (base del SLA, Req 20.6).
 * @param resueltoEn             instante UTC de resolucion, o {@code null}.
 * @param slaRespuestaCumplido   cumplimiento del tiempo de respuesta, o {@code null}.
 * @param slaResolucionCumplido  cumplimiento del tiempo de resolucion, o {@code null}.
 * @param version                version para concurrencia optimista (Req 49).
 * @param createdAt              instante de alta (UTC).
 * @param updatedAt              instante de la ultima modificacion (UTC).
 */
public record TicketServicioDto(
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

    /**
     * Proyecta una entidad {@link TicketServicio} a su DTO de salida.
     *
     * @param ticket entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static TicketServicioDto de(TicketServicio ticket) {
        return new TicketServicioDto(
                ticket.getId(),
                ticket.getContratoMantenimientoId(),
                ticket.getClienteId(),
                ticket.getOrigen().valorBd(),
                ticket.getEstado().valorBd(),
                ticket.getAsignadoTipo() == null ? null : ticket.getAsignadoTipo().valorBd(),
                ticket.getAsignadoId(),
                ticket.getAbiertoEn(),
                ticket.getResueltoEn(),
                ticket.getSlaRespuestaCumplido(),
                ticket.getSlaResolucionCumplido(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
