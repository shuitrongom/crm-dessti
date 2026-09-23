package com.dessti.crm.compras.requisicion.application;

import java.util.List;

/**
 * Comando de creacion de una {@link com.dessti.crm.compras.requisicion.domain.RequisicionCompra}
 * (Req 30.1). Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param partidas partidas de la Requisicion_Compra; al menos 1 (Req 30.1).
 */
public record CrearRequisicionCompraCommand(
        List<CrearPartidaRequisicionCommand> partidas) {
}
