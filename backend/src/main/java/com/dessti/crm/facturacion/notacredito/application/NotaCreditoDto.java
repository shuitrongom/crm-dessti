package com.dessti.crm.facturacion.notacredito.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.facturacion.notacredito.domain.NotaCredito;

/**
 * DTO de salida de una {@link NotaCredito} (Req 12.2, 37), distinto de la entidad
 * de persistencia. El estado se expone como su etiqueta de negocio
 * ({@code borrador}, {@code timbrada}, {@code cancelada}).
 *
 * @param id            identificador de la Nota de Credito.
 * @param facturaId     Factura timbrada referenciada (Req 37.1).
 * @param clienteId     Cliente al que se emite.
 * @param monto         monto del CFDI de egreso (Req 37.2).
 * @param estado        etiqueta del estado.
 * @param folioFiscal   Folio_Fiscal (UUID del SAT); {@code null} en borrador (Req 37.1).
 * @param fechaTimbrado fecha del Timbrado (UTC); {@code null} en borrador.
 * @param version       version para concurrencia optimista (Req 49).
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record NotaCreditoDto(
        UUID id,
        UUID facturaId,
        UUID clienteId,
        BigDecimal monto,
        String estado,
        UUID folioFiscal,
        Instant fechaTimbrado,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link NotaCredito} a su DTO de salida.
     *
     * @param nota entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static NotaCreditoDto de(NotaCredito nota) {
        return new NotaCreditoDto(
                nota.getId(),
                nota.getFacturaId(),
                nota.getClienteId(),
                nota.getMonto(),
                nota.getEstado().valorBd(),
                nota.getFolioFiscal(),
                nota.getFechaTimbrado(),
                nota.getVersion(),
                nota.getCreatedAt(),
                nota.getUpdatedAt());
    }
}
