package com.dessti.crm.comercial.actividad.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.comercial.actividad.domain.Actividad;

/**
 * DTO de salida de una {@link Actividad} (Req 12.2), distinto de la entidad de
 * persistencia. El controlador REST lo serializa; nunca se expone la entidad
 * JPA. El tipo y el estado se exponen como sus etiquetas de negocio
 * ({@code llamada}, {@code pendiente}, ...).
 *
 * @param id                   identificador de la Actividad.
 * @param clienteId            Cliente al que pertenece.
 * @param oportunidadId        Oportunidad vinculada; {@code null} si aplica a nivel Cliente.
 * @param tipo                 etiqueta del tipo (llamada/correo/reunion/tarea/nota).
 * @param estado               etiqueta del estado (pendiente/completada/cancelada).
 * @param asunto               titulo breve.
 * @param descripcion          detalle libre; {@code null} si no se capturo.
 * @param fechaProgramada      instante planificado (UTC).
 * @param vencimiento          fecha limite (UTC); {@code null} si no aplica.
 * @param completadaEn         sello de completado (UTC); {@code null} si no completada.
 * @param responsableUsuarioId Usuario a cargo; {@code null} si no se asigno.
 * @param version              version para concurrencia optimista (Req 49).
 * @param createdAt            instante de alta (UTC).
 * @param updatedAt            instante de la ultima modificacion (UTC).
 */
public record ActividadDto(
        UUID id,
        UUID clienteId,
        UUID oportunidadId,
        String tipo,
        String estado,
        String asunto,
        String descripcion,
        Instant fechaProgramada,
        Instant vencimiento,
        Instant completadaEn,
        UUID responsableUsuarioId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Actividad} a su DTO de salida.
     *
     * @param actividad entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ActividadDto de(Actividad actividad) {
        return new ActividadDto(
                actividad.getId(),
                actividad.getClienteId(),
                actividad.getOportunidadId(),
                actividad.getTipo().valorBd(),
                actividad.getEstado().valorBd(),
                actividad.getAsunto(),
                actividad.getDescripcion(),
                actividad.getFechaProgramada(),
                actividad.getVencimiento(),
                actividad.getCompletadaEn(),
                actividad.getResponsableUsuarioId(),
                actividad.getVersion(),
                actividad.getCreatedAt(),
                actividad.getUpdatedAt());
    }
}
