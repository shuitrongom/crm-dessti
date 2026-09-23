package com.dessti.crm.tesoreria.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.tesoreria.domain.MovimientoBancario;

/**
 * DTO de salida de un {@link MovimientoBancario} (Req 12.2, 43.2, 43.3), distinto de
 * la entidad de persistencia.
 *
 * @param id                     identificador del movimiento.
 * @param estadoCuentaBancarioId Estado_Cuenta_Bancario contenedor.
 * @param cuentaBancariaId       Cuenta_Bancaria del movimiento.
 * @param fecha                  fecha del movimiento.
 * @param monto                  monto con signo (+ deposito / - retiro).
 * @param referencia             referencia; {@code null} si no aplica.
 * @param descripcion            descripcion/concepto; {@code null} si no aplica.
 * @param estadoConciliacion     etiqueta del estado de conciliacion.
 * @param polizaContableId       Poliza_Contable emparejada; {@code null} si no aplica.
 * @param pagoId                 Pago emparejado; {@code null} si no aplica.
 * @param version                version para concurrencia optimista (Req 49).
 * @param createdAt              instante de alta (UTC).
 * @param updatedAt              instante de la ultima modificacion (UTC).
 */
public record MovimientoBancarioDto(
        UUID id,
        UUID estadoCuentaBancarioId,
        UUID cuentaBancariaId,
        LocalDate fecha,
        BigDecimal monto,
        String referencia,
        String descripcion,
        String estadoConciliacion,
        UUID polizaContableId,
        UUID pagoId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link MovimientoBancario} a su DTO de salida.
     *
     * @param movimiento entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static MovimientoBancarioDto de(MovimientoBancario movimiento) {
        return new MovimientoBancarioDto(
                movimiento.getId(),
                movimiento.getEstadoCuentaBancarioId(),
                movimiento.getCuentaBancariaId(),
                movimiento.getFecha(),
                movimiento.getMonto(),
                movimiento.getReferencia(),
                movimiento.getDescripcion(),
                movimiento.getEstadoConciliacion().valorBd(),
                movimiento.getPolizaContableId(),
                movimiento.getPagoId(),
                movimiento.getVersion(),
                movimiento.getCreatedAt(),
                movimiento.getUpdatedAt());
    }
}
