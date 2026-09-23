package com.dessti.crm.social.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Comando de creacion de una
 * {@link com.dessti.crm.social.domain.PublicacionSocial} (Req 65.1, 65.2). Objeto
 * de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param cuentaCanalSocialId Cuenta_Canal_Social por la que se publicara; obligatorio.
 * @param contenido           contenido de la publicacion; obligatorio (Req 65.1).
 * @param fechaProgramada     instante programado de publicacion (UTC); obligatorio y
 *                            no anterior al momento actual (Req 65.2).
 */
public record CrearPublicacionSocialCommand(
        UUID cuentaCanalSocialId,
        String contenido,
        Instant fechaProgramada) {
}
