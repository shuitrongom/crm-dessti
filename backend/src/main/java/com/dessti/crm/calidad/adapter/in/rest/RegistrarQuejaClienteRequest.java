package com.dessti.crm.calidad.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para registrar una Queja_Cliente (Req 70.1, 70.8). Una queja de
 * origen social se registra pasando {@code origen = "social"} y el {@code canalSocialId}
 * de la Conversacion (el {@code clienteId} lo resuelve el modulo social).
 *
 * @param clienteId     Cliente asociado; obligatorio.
 * @param origen        etiqueta del origen (portal/social/correo/telefono/otro); obligatoria.
 * @param canalSocialId referencia al canal social de origen; opcional.
 * @param descripcion   descripcion de la queja; obligatoria.
 */
public record RegistrarQuejaClienteRequest(
        @NotNull UUID clienteId,
        @NotBlank String origen,
        UUID canalSocialId,
        @NotBlank String descripcion) {
}
