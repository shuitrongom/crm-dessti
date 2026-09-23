package com.dessti.crm.compras.ordencompra.application;

import java.util.List;
import java.util.UUID;

/**
 * Comando de creacion de una {@link com.dessti.crm.compras.ordencompra.domain.OrdenCompra}
 * (Req 31.1). Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * <p>El {@code requisicionCompraId} es {@code null} en el alta directa; se fija
 * cuando la Orden se genera desde una Requisicion_Compra aprobada (Req 30.3), caso
 * en que la capa de aplicacion del submodulo de Requisiciones invoca al de Ordenes
 * de Compra.</p>
 *
 * @param proveedorId         Proveedor existente al que se asocia; obligatorio (Req 31.1).
 * @param requisicionCompraId Requisicion_Compra de origen; {@code null} en alta directa.
 * @param partidas            partidas de la Orden_Compra; entre 1 y 500 (Req 31.1).
 */
public record CrearOrdenCompraCommand(
        UUID proveedorId,
        UUID requisicionCompraId,
        List<CrearPartidaOrdenCompraCommand> partidas) {
}
