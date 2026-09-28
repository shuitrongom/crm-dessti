package com.dessti.crm.comercial.actividad.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Comando de creacion de una {@link com.dessti.crm.comercial.actividad.domain.Actividad}.
 * Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param clienteId            Cliente al que pertenece; obligatorio.
 * @param oportunidadId        Oportunidad del Cliente a vincular; {@code null} para
 *                             una actividad a nivel Cliente.
 * @param tipo                 etiqueta del tipo (llamada/correo/reunion/tarea/nota);
 *                             obligatoria.
 * @param asunto               titulo breve; obligatorio (1..200).
 * @param descripcion          detalle libre; opcional.
 * @param fechaProgramada      instante planificado; obligatorio.
 * @param vencimiento          fecha limite; opcional, no anterior a la fecha programada.
 * @param responsableUsuarioId Usuario a cargo; opcional.
 */
public record CrearActividadCommand(
        UUID clienteId,
        UUID oportunidadId,
        String tipo,
        String asunto,
        String descripcion,
        Instant fechaProgramada,
        Instant vencimiento,
        UUID responsableUsuarioId) {
}
