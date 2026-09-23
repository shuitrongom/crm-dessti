package com.dessti.crm.facturacion.factura.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vista minima de una {@code Orden_Fabricacion} necesaria para emitir una Factura
 * a partir de ella (Req 34.1). Objeto de solo lectura, libre de dependencias de
 * persistencia, que expone el {@link OrdenFabricacionParaFacturaPort}.
 *
 * @param ordenFabricacionId identificador de la Orden_Fabricacion.
 * @param clienteId          Cliente de la OF, que la Factura denormaliza (Req 34.4).
 * @param total              importe base para el calculo fiscal, tomado de la
 *                           Cotizacion de origen de la OF (Req 34.2).
 */
public record OrdenFabricacionParaFactura(UUID ordenFabricacionId, UUID clienteId, BigDecimal total) {
}
