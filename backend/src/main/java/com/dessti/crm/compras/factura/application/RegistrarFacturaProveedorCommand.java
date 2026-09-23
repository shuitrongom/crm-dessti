package com.dessti.crm.compras.factura.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de registro de una {@link com.dessti.crm.compras.factura.domain.FacturaProveedor}
 * (Req 33.1, 33.2). Objeto de entrada de la capa de aplicacion, distinto de la
 * entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado. El Proveedor no
 * se aporta: la capa de aplicacion lo deriva de la Orden_Compra asociada
 * (denormalizado para el filtro del listado, Req 33.8).</p>
 *
 * @param ordenCompraId  Orden_Compra existente asociada; obligatorio (Req 33.1).
 * @param folioProveedor folio de factura del Proveedor; obligatorio (Req 33.2).
 * @param monto          monto de la factura; obligatorio, &gt;= 0 (Req 33.2).
 */
public record RegistrarFacturaProveedorCommand(
        UUID ordenCompraId,
        String folioProveedor,
        BigDecimal monto) {
}
