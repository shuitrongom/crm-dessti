package com.dessti.crm.social.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para crear una Publicacion_Social (Req 65.1, 65.2). DTO de
 * entrada del contrato REST, distinto de la entidad JPA. El Canal_Social se deriva
 * de la Cuenta_Canal_Social indicada; no se envia en el cuerpo.
 *
 * @param cuentaCanalSocialId Cuenta_Canal_Social por la que se publicara; obligatorio.
 * @param contenido           contenido de la publicacion; obligatorio (Req 65.1).
 * @param fechaProgramada     instante programado de publicacion (UTC); obligatorio y
 *                            no anterior al momento actual (Req 65.2).
 */
public record CrearPublicacionSocialRequest(
        @NotNull UUID cuentaCanalSocialId,
        @NotBlank String contenido,
        @NotNull Instant fechaProgramada) {
}
