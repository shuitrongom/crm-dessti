package com.dessti.crm.platform.security.sesiones.rest;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.security.sesiones.SesionActivaView;

/**
 * DTO de respuesta REST de una Sesion (Token_Refresco) activa (Req 68.5).
 * Desacoplado de la entidad JPA; NUNCA expone el valor del Token_Refresco, solo
 * su identificador ({@code jti}) y metadatos.
 *
 * @param jti       identificador unico del Token_Refresco (claim {@code jti}).
 * @param usuarioId cuenta duena de la sesion.
 * @param tenantId  empresa del Usuario; {@code null} para super_admin.
 * @param emitidoEn instante de emision (UTC).
 * @param expiraEn  instante de expiracion (UTC).
 */
public record SesionActivaResponse(
        String jti,
        UUID usuarioId,
        UUID tenantId,
        Instant emitidoEn,
        Instant expiraEn) {

    /** Mapea la vista de dominio a su DTO de respuesta. */
    public static SesionActivaResponse de(SesionActivaView vista) {
        return new SesionActivaResponse(
                vista.jti(), vista.usuarioId(), vista.tenantId(),
                vista.emitidoEn(), vista.expiraEn());
    }
}
