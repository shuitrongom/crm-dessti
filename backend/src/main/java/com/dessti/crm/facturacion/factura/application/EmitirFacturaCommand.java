package com.dessti.crm.facturacion.factura.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de emision de una {@link com.dessti.crm.facturacion.factura.domain.Factura}
 * (Req 34.1, 34.2). Objeto de entrada de la capa de aplicacion, distinto de la
 * entidad. Exactamente uno de {@code cotizacionId} u {@code ordenFabricacionId}
 * debe indicarse como origen (Req 34.1); la capa de aplicacion lo valida.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param cotizacionId         Cotizacion aprobada de origen; {@code null} si el origen es una OF.
 * @param ordenFabricacionId   Orden_Fabricacion de origen; {@code null} si el origen es una Cotizacion.
 * @param receptorRfc          RFC del receptor; obligatorio (Req 34.1).
 * @param receptorNombre       nombre o razon social del receptor; obligatorio (Req 34.1).
 * @param receptorCp           codigo postal del receptor; obligatorio (Req 34.1).
 * @param receptorRegimenFiscal clave del regimen fiscal del receptor; obligatoria (Req 34.1).
 * @param usoCfdi              clave del uso de CFDI; obligatoria (Req 34.1).
 * @param tasaRetencion        tasa de retencion aplicable en {@code [0, 1]}; {@code null}
 *                             se interpreta como sin retencion (Req 34.2).
 */
public record EmitirFacturaCommand(
        UUID cotizacionId,
        UUID ordenFabricacionId,
        String receptorRfc,
        String receptorNombre,
        String receptorCp,
        String receptorRegimenFiscal,
        String usoCfdi,
        BigDecimal tasaRetencion) {
}
