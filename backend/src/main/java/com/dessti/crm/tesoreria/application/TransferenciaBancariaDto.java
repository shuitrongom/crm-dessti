package com.dessti.crm.tesoreria.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.tesoreria.domain.TransferenciaBancaria;

/**
 * DTO de salida de una {@link TransferenciaBancaria} (Req 43), distinto de la entidad
 * de persistencia.
 *
 * @param id              identificador de la transferencia.
 * @param cuentaOrigenId  Cuenta_Bancaria de origen.
 * @param cuentaDestinoId Cuenta_Bancaria de destino.
 * @param monto           monto transferido (escala 2).
 * @param fecha           fecha del traspaso.
 * @param concepto        concepto descriptivo; {@code null} si no se indico.
 * @param fechaRegistro   instante de registro (UTC).
 * @param version         version para concurrencia optimista (Req 49).
 * @param createdAt       instante de alta (UTC).
 * @param updatedAt       instante de la ultima modificacion (UTC).
 */
public record TransferenciaBancariaDto(
        UUID id,
        UUID cuentaOrigenId,
        UUID cuentaDestinoId,
        BigDecimal monto,
        LocalDate fecha,
        String concepto,
        Instant fechaRegistro,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link TransferenciaBancaria} a su DTO de salida.
     *
     * @param transferencia entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static TransferenciaBancariaDto de(TransferenciaBancaria transferencia) {
        return new TransferenciaBancariaDto(
                transferencia.getId(),
                transferencia.getCuentaOrigenId(),
                transferencia.getCuentaDestinoId(),
                transferencia.getMonto(),
                transferencia.getFecha(),
                transferencia.getConcepto(),
                transferencia.getFechaRegistro(),
                transferencia.getVersion(),
                transferencia.getCreatedAt(),
                transferencia.getUpdatedAt());
    }
}
