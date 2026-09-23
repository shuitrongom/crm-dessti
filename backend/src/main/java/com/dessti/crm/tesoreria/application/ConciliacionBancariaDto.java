package com.dessti.crm.tesoreria.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.tesoreria.domain.ConciliacionBancaria;

/**
 * DTO de salida de una {@link ConciliacionBancaria} (Req 12.2, 43.5), distinto de la
 * entidad de persistencia.
 *
 * @param id                     identificador de la conciliacion.
 * @param cuentaBancariaId       Cuenta_Bancaria conciliada.
 * @param estadoCuentaBancarioId Estado_Cuenta_Bancario conciliado.
 * @param saldoBancario          saldo bancario (saldo final del estado de cuenta).
 * @param saldoContable          saldo contable derivado de las partidas conciliadas.
 * @param diferencia             diferencia {@code saldo_bancario - saldo_contable}.
 * @param estado                 etiqueta del estado (en_proceso / completa).
 * @param fecha                  instante de la conciliacion (UTC).
 * @param version                version para concurrencia optimista (Req 49).
 * @param createdAt              instante de alta (UTC).
 * @param updatedAt              instante de la ultima modificacion (UTC).
 */
public record ConciliacionBancariaDto(
        UUID id,
        UUID cuentaBancariaId,
        UUID estadoCuentaBancarioId,
        BigDecimal saldoBancario,
        BigDecimal saldoContable,
        BigDecimal diferencia,
        String estado,
        Instant fecha,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ConciliacionBancaria} a su DTO de salida.
     *
     * @param conciliacion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ConciliacionBancariaDto de(ConciliacionBancaria conciliacion) {
        return new ConciliacionBancariaDto(
                conciliacion.getId(),
                conciliacion.getCuentaBancariaId(),
                conciliacion.getEstadoCuentaBancarioId(),
                conciliacion.getSaldoBancario(),
                conciliacion.getSaldoContable(),
                conciliacion.getDiferencia(),
                conciliacion.getEstado().valorBd(),
                conciliacion.getFecha(),
                conciliacion.getVersion(),
                conciliacion.getCreatedAt(),
                conciliacion.getUpdatedAt());
    }
}
