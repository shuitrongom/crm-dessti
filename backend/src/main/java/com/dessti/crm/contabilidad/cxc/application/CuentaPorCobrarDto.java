package com.dessti.crm.contabilidad.cxc.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.contabilidad.cxc.domain.CuentaPorCobrar;

/**
 * DTO de salida de una {@link CuentaPorCobrar} (Req 12.2, 36), distinto de la
 * entidad de persistencia. El estado se expone como su etiqueta de negocio
 * ({@code pendiente}, {@code parcial}, {@code pagada}, {@code cancelada}).
 *
 * @param id               identificador de la CxC.
 * @param facturaId        Factura timbrada de origen (Req 36.1).
 * @param clienteId        Cliente al que se le cobra.
 * @param total            total original de la Factura (Req 36.1).
 * @param saldo            saldo pendiente de cobro (Req 36.2).
 * @param estado           etiqueta del estado.
 * @param fechaEmision     instante de registro de la CxC (UTC).
 * @param fechaVencimiento fecha de vencimiento para el aging; {@code null} si no aplica.
 * @param version          version para concurrencia optimista (Req 49).
 * @param createdAt        instante de alta (UTC).
 * @param updatedAt        instante de la ultima modificacion (UTC).
 */
public record CuentaPorCobrarDto(
        UUID id,
        UUID facturaId,
        UUID clienteId,
        BigDecimal total,
        BigDecimal saldo,
        String estado,
        Instant fechaEmision,
        LocalDate fechaVencimiento,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link CuentaPorCobrar} a su DTO de salida.
     *
     * @param cxc entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static CuentaPorCobrarDto de(CuentaPorCobrar cxc) {
        return new CuentaPorCobrarDto(
                cxc.getId(),
                cxc.getFacturaId(),
                cxc.getClienteId(),
                cxc.getTotal(),
                cxc.getSaldo(),
                cxc.getEstado().valorBd(),
                cxc.getFechaEmision(),
                cxc.getFechaVencimiento(),
                cxc.getVersion(),
                cxc.getCreatedAt(),
                cxc.getUpdatedAt());
    }
}
