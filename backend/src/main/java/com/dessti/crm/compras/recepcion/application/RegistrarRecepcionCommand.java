package com.dessti.crm.compras.recepcion.application;

import java.util.List;
import java.util.UUID;

/**
 * Comando de registro de una {@link com.dessti.crm.compras.recepcion.domain.RecepcionMercancia}
 * contra una Orden_Compra (Req 32.1). Objeto de entrada de la capa de aplicacion,
 * distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param ordenCompraId Orden_Compra contra la que se recibe; obligatorio (Req 32.1).
 * @param partidas      renglones recibidos (partida de OC + cantidad); al menos uno.
 */
public record RegistrarRecepcionCommand(
        UUID ordenCompraId,
        List<RegistrarPartidaRecepcionCommand> partidas) {
}
