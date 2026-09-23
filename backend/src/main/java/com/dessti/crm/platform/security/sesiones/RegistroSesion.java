package com.dessti.crm.platform.security.sesiones;

import java.time.Instant;
import java.util.UUID;

/**
 * Comando de dominio para registrar una Sesion (Token_Refresco) recien emitida
 * en el almacen de sesiones (Req 68.3). Es un objeto de entrada inmutable, sin
 * dependencias de framework, que el caso de uso de {@code login} (y, si hay
 * rotacion, el de {@code refresh}) construye para dar de alta la sesion.
 *
 * <p><strong>Sin secretos:</strong> este comando NUNCA transporta el valor del
 * Token_Refresco; solo su identificador ({@code jti}) y los metadatos de la
 * sesion. Asi el almacen puede listar sesiones activas y sustentar la denylist
 * sin exponer credenciales (Req 10.10, 11.3).</p>
 *
 * @param jti       identificador unico del Token_Refresco (claim {@code jti});
 *                  obligatorio.
 * @param usuarioId identificador de la cuenta duena de la sesion; obligatorio.
 * @param tenantId  empresa del Usuario; {@code null} para super_admin (Req 24.3).
 * @param emitidoEn instante de emision del Token_Refresco (UTC); obligatorio.
 * @param expiraEn  instante de expiracion del Token_Refresco (UTC); obligatorio.
 */
public record RegistroSesion(
        String jti,
        UUID usuarioId,
        UUID tenantId,
        Instant emitidoEn,
        Instant expiraEn) {

    /**
     * Valida las invariantes minimas del comando.
     *
     * @throws IllegalArgumentException si {@code jti}, {@code usuarioId},
     *         {@code emitidoEn} o {@code expiraEn} son nulos/vacios.
     */
    public RegistroSesion {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("El jti de la sesion es obligatorio");
        }
        if (usuarioId == null) {
            throw new IllegalArgumentException("El usuarioId de la sesion es obligatorio");
        }
        if (emitidoEn == null) {
            throw new IllegalArgumentException("El instante de emision es obligatorio");
        }
        if (expiraEn == null) {
            throw new IllegalArgumentException("El instante de expiracion es obligatorio");
        }
    }
}
