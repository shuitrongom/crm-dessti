package com.dessti.crm.tesoreria.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.tesoreria.domain.EstadoCuentaBancario;

/**
 * DTO de salida de un {@link EstadoCuentaBancario} con el desglose de sus
 * movimientos (Req 12.2, 43.2), distinto de la entidad de persistencia.
 *
 * @param id               identificador del estado de cuenta.
 * @param cuentaBancariaId Cuenta_Bancaria del estado de cuenta.
 * @param periodoInicio    inicio del periodo.
 * @param periodoFin       fin del periodo.
 * @param saldoInicial     saldo inicial del periodo.
 * @param saldoFinal       saldo final del periodo (saldo bancario).
 * @param movimientos      desglose de movimientos importados.
 * @param version          version para concurrencia optimista (Req 49).
 * @param createdAt        instante de alta (UTC).
 * @param updatedAt        instante de la ultima modificacion (UTC).
 */
public record EstadoCuentaBancarioDto(
        UUID id,
        UUID cuentaBancariaId,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        BigDecimal saldoInicial,
        BigDecimal saldoFinal,
        List<MovimientoBancarioDto> movimientos,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link EstadoCuentaBancario} a su DTO de salida con sus
     * movimientos.
     *
     * @param estado entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static EstadoCuentaBancarioDto de(EstadoCuentaBancario estado) {
        List<MovimientoBancarioDto> movimientos = estado.getMovimientos().stream()
                .map(MovimientoBancarioDto::de)
                .toList();
        return new EstadoCuentaBancarioDto(
                estado.getId(),
                estado.getCuentaBancariaId(),
                estado.getPeriodoInicio(),
                estado.getPeriodoFin(),
                estado.getSaldoInicial(),
                estado.getSaldoFinal(),
                movimientos,
                estado.getVersion(),
                estado.getCreatedAt(),
                estado.getUpdatedAt());
    }
}
