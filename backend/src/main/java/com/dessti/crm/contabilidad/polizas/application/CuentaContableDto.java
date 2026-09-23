package com.dessti.crm.contabilidad.polizas.application;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.contabilidad.polizas.domain.CuentaContable;

/**
 * DTO de salida de una {@link CuentaContable} (Req 12.2, 38.1), distinto de la
 * entidad de persistencia. El tipo y la naturaleza se exponen como su etiqueta de
 * negocio.
 *
 * @param id         identificador de la cuenta.
 * @param codigo     codigo de la cuenta (unico por tenant).
 * @param nombre     nombre descriptivo.
 * @param tipo       etiqueta del tipo contable.
 * @param naturaleza etiqueta de la naturaleza del saldo.
 * @param activa     {@code true} si la cuenta esta activa.
 * @param version    version para concurrencia optimista (Req 49).
 * @param createdAt  instante de alta (UTC).
 * @param updatedAt  instante de la ultima modificacion (UTC).
 */
public record CuentaContableDto(
        UUID id,
        String codigo,
        String nombre,
        String tipo,
        String naturaleza,
        boolean activa,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link CuentaContable} a su DTO de salida.
     *
     * @param cuenta entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static CuentaContableDto de(CuentaContable cuenta) {
        return new CuentaContableDto(
                cuenta.getId(),
                cuenta.getCodigo(),
                cuenta.getNombre(),
                cuenta.getTipo().valorBd(),
                cuenta.getNaturaleza().valorBd(),
                cuenta.isActiva(),
                cuenta.getVersion(),
                cuenta.getCreatedAt(),
                cuenta.getUpdatedAt());
    }
}
