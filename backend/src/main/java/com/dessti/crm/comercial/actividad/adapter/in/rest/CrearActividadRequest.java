package com.dessti.crm.comercial.actividad.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion REST de alta de una Actividad de seguimiento. Contrato de
 * entrada distinto de la entidad JPA (Req 12.2). Las validaciones de formato
 * basicas se declaran con Bean Validation; las reglas de negocio finas (rango de
 * fechas, coherencia) las aplica el dominio.
 *
 * @param clienteId            Cliente al que pertenece; obligatorio.
 * @param oportunidadId        Oportunidad del Cliente a vincular; opcional.
 * @param tipo                 etiqueta del tipo (llamada/correo/reunion/tarea/nota);
 *                             obligatoria.
 * @param asunto               titulo breve; obligatorio (1..200).
 * @param descripcion          detalle libre; opcional (<= 4000).
 * @param fechaProgramada      instante planificado (UTC); obligatorio.
 * @param vencimiento          fecha limite (UTC); opcional.
 * @param responsableUsuarioId Usuario a cargo; opcional.
 */
public record CrearActividadRequest(
        @NotNull(message = "El Cliente de la Actividad es obligatorio.")
        UUID clienteId,

        UUID oportunidadId,

        @NotBlank(message = "El tipo de la Actividad es obligatorio.")
        String tipo,

        @NotBlank(message = "El asunto de la Actividad es obligatorio.")
        @Size(max = 200, message = "El asunto no puede exceder 200 caracteres.")
        String asunto,

        @Size(max = 4000, message = "La descripcion no puede exceder 4000 caracteres.")
        String descripcion,

        @NotNull(message = "La fecha programada de la Actividad es obligatoria.")
        Instant fechaProgramada,

        Instant vencimiento,

        UUID responsableUsuarioId) {
}
