package com.dessti.crm.contabilidad.cxp.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.contabilidad.cxp.domain.ProgramacionPago;

/**
 * DTO de salida de una {@link ProgramacionPago} (Req 12.2, 42.2), distinto de la
 * entidad de persistencia.
 *
 * @param id               identificador de la Programacion_Pago.
 * @param cuentaPorPagarId Cuenta_Por_Pagar de origen (Req 42.2).
 * @param fechaProgramada  fecha programada del pago (Req 42.2).
 * @param monto            monto programado del pago (Req 42.2).
 * @param aplicada         {@code true} si el pago programado ya se aplico (Req 42.3).
 * @param version          version para concurrencia optimista (Req 49).
 * @param createdAt        instante de alta (UTC).
 * @param updatedAt        instante de la ultima modificacion (UTC).
 */
public record ProgramacionPagoDto(
        UUID id,
        UUID cuentaPorPagarId,
        LocalDate fechaProgramada,
        BigDecimal monto,
        boolean aplicada,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ProgramacionPago} a su DTO de salida.
     *
     * @param programacion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ProgramacionPagoDto de(ProgramacionPago programacion) {
        return new ProgramacionPagoDto(
                programacion.getId(),
                programacion.getCuentaPorPagarId(),
                programacion.getFechaProgramada(),
                programacion.getMonto(),
                programacion.isAplicada(),
                programacion.getVersion(),
                programacion.getCreatedAt(),
                programacion.getUpdatedAt());
    }
}
