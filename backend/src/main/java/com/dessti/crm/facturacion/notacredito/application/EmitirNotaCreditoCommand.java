package com.dessti.crm.facturacion.notacredito.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de emision de una {@link com.dessti.crm.facturacion.notacredito.domain.NotaCredito}
 * (Req 37.1, 37.2). Objeto de entrada de la capa de aplicacion, distinto de la
 * entidad. No incluye el {@code tenant_id} (se deriva del contexto, Req 23.4).
 *
 * @param facturaId Factura timbrada referenciada; obligatoria (Req 37.1).
 * @param monto     monto del CFDI de egreso; positivo y acotado por el saldo (Req 37.2).
 */
public record EmitirNotaCreditoCommand(UUID facturaId, BigDecimal monto) {
}
