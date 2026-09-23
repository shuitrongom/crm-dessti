package com.dessti.crm.contabilidad.cxc.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.contabilidad.cxc.domain.AplicacionPago;
import com.dessti.crm.contabilidad.cxc.domain.PagoCliente;

/**
 * DTO de salida de un {@link PagoCliente} con el desglose de sus aplicaciones a
 * Facturas (Req 12.2, 36.2, 36.4), distinto de la entidad de persistencia.
 *
 * @param id                       identificador del Pago_Cliente.
 * @param clienteId                Cliente que pago (Req 36.2).
 * @param monto                    monto total del pago (Req 36.2).
 * @param fechaPago                instante del pago (UTC).
 * @param formaPago                forma de pago (clave SAT); {@code null} si no se indico.
 * @param esParcialidad            {@code true} si es parcialidad/diferido (Req 36.4).
 * @param complementoFolioFiscal   Folio_Fiscal del Complemento_Pago; {@code null} si no aplica.
 * @param complementoFechaTimbrado fecha del Timbrado del complemento; {@code null} si no aplica.
 * @param aplicaciones             desglose de la aplicacion del pago por Factura.
 * @param version                  version para concurrencia optimista (Req 49).
 * @param createdAt                instante de alta (UTC).
 * @param updatedAt                instante de la ultima modificacion (UTC).
 */
public record PagoClienteDto(
        UUID id,
        UUID clienteId,
        BigDecimal monto,
        Instant fechaPago,
        String formaPago,
        boolean esParcialidad,
        UUID complementoFolioFiscal,
        Instant complementoFechaTimbrado,
        List<AplicacionPagoDto> aplicaciones,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta un {@link PagoCliente} y sus aplicaciones a su DTO de salida.
     *
     * @param pago         entidad de pago a proyectar.
     * @param aplicaciones aplicaciones del pago a Facturas.
     * @return el DTO correspondiente.
     */
    public static PagoClienteDto de(PagoCliente pago, List<AplicacionPago> aplicaciones) {
        List<AplicacionPagoDto> lineas = aplicaciones.stream().map(AplicacionPagoDto::de).toList();
        return new PagoClienteDto(
                pago.getId(),
                pago.getClienteId(),
                pago.getMonto(),
                pago.getFechaPago(),
                pago.getFormaPago(),
                pago.isEsParcialidad(),
                pago.getComplementoFolioFiscal(),
                pago.getComplementoFechaTimbrado(),
                lineas,
                pago.getVersion(),
                pago.getCreatedAt(),
                pago.getUpdatedAt());
    }
}
