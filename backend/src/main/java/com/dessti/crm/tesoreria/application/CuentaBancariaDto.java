package com.dessti.crm.tesoreria.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.tesoreria.domain.CuentaBancaria;

/**
 * DTO de salida de una {@link CuentaBancaria} (Req 12.2, 43.1), distinto de la
 * entidad de persistencia.
 *
 * @param id        identificador de la cuenta.
 * @param nombre    nombre descriptivo.
 * @param banco     banco de la cuenta.
 * @param clabe     CLABE interbancaria; {@code null} si no aplica.
 * @param moneda    moneda ISO 4217.
 * @param activa    bandera de actividad.
 * @param version   version para concurrencia optimista (Req 49).
 * @param createdAt instante de alta (UTC).
 * @param updatedAt instante de la ultima modificacion (UTC).
 */
public record CuentaBancariaDto(
        UUID id,
        String nombre,
        String banco,
        String clabe,
        String moneda,
        boolean activa,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link CuentaBancaria} a su DTO de salida.
     *
     * @param cuenta entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static CuentaBancariaDto de(CuentaBancaria cuenta) {
        return new CuentaBancariaDto(
                cuenta.getId(),
                cuenta.getNombre(),
                cuenta.getBanco(),
                cuenta.getClabe(),
                cuenta.getMoneda(),
                cuenta.isActiva(),
                cuenta.getVersion(),
                cuenta.getCreatedAt(),
                cuenta.getUpdatedAt());
    }
}
