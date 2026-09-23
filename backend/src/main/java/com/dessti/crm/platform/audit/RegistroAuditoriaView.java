package com.dessti.crm.platform.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de solo lectura de un registro de la bitacora de auditoria, usada como
 * resultado de las consultas y exportaciones (Req 10.5, 10.9). Es un DTO de
 * dominio inmutable, desacoplado de la entidad JPA {@link RegistroAuditoria}.
 *
 * <p>Se exponen el {@code hashActual} y el {@code hashPrevio} para permitir, en
 * la Tarea 7.2, la verificacion de integridad de la cadena bajo demanda sin
 * acoplar al consumidor a la entidad de persistencia.</p>
 *
 * @param id             identificador secuencial (orden de la cadena).
 * @param tenantId       empresa del evento; {@code null} para el ambito de
 *                       plataforma.
 * @param actor          actor de la accion.
 * @param accion         accion ejecutada.
 * @param recurso        recurso afectado.
 * @param detalle        detalle legible (sin secretos).
 * @param valorAnterior  JSON del estado previo (sin secretos).
 * @param valorNuevo     JSON del estado nuevo (sin secretos).
 * @param traceId        identificador de correlacion (Req 10.12).
 * @param timestampUtc   marca temporal UTC del evento.
 * @param hashPrevio     hash del registro anterior en la cadena.
 * @param hashActual     hash de este registro.
 */
public record RegistroAuditoriaView(
        long id,
        UUID tenantId,
        String actor,
        String accion,
        String recurso,
        String detalle,
        String valorAnterior,
        String valorNuevo,
        String traceId,
        Instant timestampUtc,
        String hashPrevio,
        String hashActual) {
}
