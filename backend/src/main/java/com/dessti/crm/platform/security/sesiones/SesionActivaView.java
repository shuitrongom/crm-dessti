package com.dessti.crm.platform.security.sesiones;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de solo lectura de una Sesion (Token_Refresco) <b>activa</b>, apta para
 * el listado paginado de sesiones (Req 68.5). Es un DTO desacoplado de la
 * entidad JPA {@code SesionRefresco}; NUNCA expone el valor del Token_Refresco,
 * solo su identificador ({@code jti}) y metadatos.
 *
 * @param jti       identificador unico del Token_Refresco (claim {@code jti}).
 * @param usuarioId cuenta duena de la sesion.
 * @param tenantId  empresa del Usuario; {@code null} para super_admin.
 * @param emitidoEn instante de emision (UTC).
 * @param expiraEn  instante de expiracion (UTC).
 */
public record SesionActivaView(
        String jti,
        UUID usuarioId,
        UUID tenantId,
        Instant emitidoEn,
        Instant expiraEn) {
}
