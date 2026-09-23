package com.dessti.crm.facturacion.factura.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.facturacion.factura.domain.Factura;

/**
 * DTO de salida de una {@link Factura} (Req 12.2, 34, 35), distinto de la entidad
 * de persistencia. El controlador REST lo serializa; nunca se expone la entidad
 * JPA. El estado se expone como su etiqueta de negocio ({@code borrador},
 * {@code timbrada}, {@code cancelacion_en_proceso}, {@code cancelada}).
 *
 * @param id                   identificador de la Factura (Req 34.1).
 * @param cotizacionId         Cotizacion de origen; {@code null} si el origen es una OF.
 * @param ordenFabricacionId   Orden_Fabricacion de origen; {@code null} si el origen es una Cotizacion.
 * @param clienteId            Cliente al que se emite (Req 34.4).
 * @param receptorRfc          RFC del receptor (Req 34.1).
 * @param receptorNombre       nombre o razon social del receptor (Req 34.1).
 * @param receptorCp           codigo postal del receptor (Req 34.1).
 * @param receptorRegimenFiscal clave del regimen fiscal del receptor (Req 34.1).
 * @param usoCfdi              clave del uso de CFDI (Req 34.1).
 * @param subtotal             subtotal (escala 2, Req 34.2).
 * @param iva                  IVA trasladado (Req 34.2).
 * @param retenciones          retenciones (Req 34.2).
 * @param total                total = subtotal + IVA - retenciones (Req 34.2).
 * @param estado               etiqueta del estado (Req 35.7).
 * @param folioFiscal          Folio_Fiscal (UUID del SAT); {@code null} en borrador (Req 35.1).
 * @param fechaTimbrado        fecha del Timbrado (UTC); {@code null} en borrador (Req 35.1).
 * @param motivoCancelacion    motivo SAT de cancelacion; {@code null} si no se cancela (Req 35.4).
 * @param version              version para concurrencia optimista (Req 49).
 * @param createdAt            instante de alta (UTC).
 * @param updatedAt            instante de la ultima modificacion (UTC).
 */
public record FacturaDto(
        UUID id,
        UUID cotizacionId,
        UUID ordenFabricacionId,
        UUID clienteId,
        String receptorRfc,
        String receptorNombre,
        String receptorCp,
        String receptorRegimenFiscal,
        String usoCfdi,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal retenciones,
        BigDecimal total,
        String estado,
        UUID folioFiscal,
        Instant fechaTimbrado,
        String motivoCancelacion,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Factura} a su DTO de salida.
     *
     * @param factura entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static FacturaDto de(Factura factura) {
        return new FacturaDto(
                factura.getId(),
                factura.getCotizacionId(),
                factura.getOrdenFabricacionId(),
                factura.getClienteId(),
                factura.getReceptorRfc(),
                factura.getReceptorNombre(),
                factura.getReceptorCp(),
                factura.getReceptorRegimenFiscal(),
                factura.getUsoCfdi(),
                factura.getSubtotal(),
                factura.getIva(),
                factura.getRetenciones(),
                factura.getTotal(),
                factura.getEstado().valorBd(),
                factura.getFolioFiscal(),
                factura.getFechaTimbrado(),
                factura.getMotivoCancelacion(),
                factura.getVersion(),
                factura.getCreatedAt(),
                factura.getUpdatedAt());
    }
}
