package com.dessti.crm.notificaciones.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.notificaciones.domain.Notificacion;

/**
 * DTO de salida de una {@link Notificacion} (Req 46, 12.2), distinto de la entidad
 * de persistencia. El controlador REST lo serializa; nunca se expone la entidad JPA.
 * El evento, el canal y el estado se exponen como sus etiquetas de negocio.
 *
 * @param id             identificador de la Notificacion.
 * @param eventoOrigen   etiqueta del evento de origen (Req 46.1).
 * @param canal          etiqueta del canal de entrega (Req 46.1).
 * @param destinatario   correo/telefono/id social del destinatario.
 * @param asunto         asunto (correo); puede ser {@code null}.
 * @param contenido      contenido minimo del aviso (Req 46.4).
 * @param esMarketing    si la Notificacion es de marketing (Req 46.7).
 * @param referenciaTipo tipo del recurso referenciado; puede ser {@code null}.
 * @param referenciaId   identificador del recurso referenciado; puede ser {@code null}.
 * @param estado         etiqueta del estado (pendiente/enviada/fallida/omitida).
 * @param motivoOmision  motivo de la omision cuando el estado es {@code omitida}
 *                       (Req 46.7); {@code null} en otro caso.
 * @param creadaEn       instante de generacion (UTC).
 * @param enviadaEn      instante de envio exitoso (UTC); {@code null} si no se envio.
 * @param version        version para concurrencia optimista (Req 49).
 * @param createdAt      instante de alta (UTC).
 * @param updatedAt      instante de la ultima modificacion (UTC).
 */
public record NotificacionDto(
        UUID id,
        String eventoOrigen,
        String canal,
        String destinatario,
        String asunto,
        String contenido,
        boolean esMarketing,
        String referenciaTipo,
        UUID referenciaId,
        String estado,
        String motivoOmision,
        Instant creadaEn,
        Instant enviadaEn,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Notificacion} a su DTO de salida.
     *
     * @param notificacion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static NotificacionDto de(Notificacion notificacion) {
        return new NotificacionDto(
                notificacion.getId(),
                notificacion.getEventoOrigen().valorBd(),
                notificacion.getCanal().valorBd(),
                notificacion.getDestinatario(),
                notificacion.getAsunto(),
                notificacion.getContenido(),
                notificacion.isEsMarketing(),
                notificacion.getReferenciaTipo(),
                notificacion.getReferenciaId(),
                notificacion.getEstado().valorBd(),
                notificacion.getMotivoOmision(),
                notificacion.getCreadaEn(),
                notificacion.getEnviadaEn(),
                notificacion.getVersion(),
                notificacion.getCreatedAt(),
                notificacion.getUpdatedAt());
    }
}
