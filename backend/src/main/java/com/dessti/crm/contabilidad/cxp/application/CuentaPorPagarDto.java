package com.dessti.crm.contabilidad.cxp.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.contabilidad.cxp.domain.CuentaPorPagar;

/**
 * DTO de salida de una {@link CuentaPorPagar} (Req 12.2, 42), distinto de la entidad
 * de persistencia. El estado se expone como su etiqueta de negocio
 * ({@code pendiente}, {@code parcial}, {@code pagada}, {@code cancelada}).
 *
 * @param id                 identificador de la CxP.
 * @param facturaProveedorId Factura_Proveedor conciliada de origen (Req 42.1).
 * @param proveedorId        Proveedor al que se le paga.
 * @param total              total original de la factura (Req 42.1).
 * @param saldo              saldo pendiente de pago (Req 42.3).
 * @param estado             etiqueta del estado.
 * @param fechaRegistro      instante de registro de la CxP (UTC).
 * @param fechaVencimiento   fecha de vencimiento para el aging; {@code null} si no aplica.
 * @param version            version para concurrencia optimista (Req 49).
 * @param createdAt          instante de alta (UTC).
 * @param updatedAt          instante de la ultima modificacion (UTC).
 */
public record CuentaPorPagarDto(
        UUID id,
        UUID facturaProveedorId,
        UUID proveedorId,
        BigDecimal total,
        BigDecimal saldo,
        String estado,
        Instant fechaRegistro,
        LocalDate fechaVencimiento,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link CuentaPorPagar} a su DTO de salida.
     *
     * @param cxp entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static CuentaPorPagarDto de(CuentaPorPagar cxp) {
        return new CuentaPorPagarDto(
                cxp.getId(),
                cxp.getFacturaProveedorId(),
                cxp.getProveedorId(),
                cxp.getTotal(),
                cxp.getSaldo(),
                cxp.getEstado().valorBd(),
                cxp.getFechaRegistro(),
                cxp.getFechaVencimiento(),
                cxp.getVersion(),
                cxp.getCreatedAt(),
                cxp.getUpdatedAt());
    }
}
