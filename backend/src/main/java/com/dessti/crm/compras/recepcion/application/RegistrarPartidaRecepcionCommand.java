package com.dessti.crm.compras.recepcion.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de registro de un renglon de recepcion dentro de una
 * {@link com.dessti.crm.compras.recepcion.domain.RecepcionMercancia} (Req 32.1).
 * Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p>Identifica la Partida_Orden_Compra contra la que se recibe y la cantidad
 * recibida ahora; el Material se deriva de la propia partida de la Orden_Compra
 * (no lo aporta el cliente) para garantizar la integridad de la entrada de
 * inventario (Req 32.4).</p>
 *
 * @param partidaOrdenCompraId Partida_Orden_Compra contra la que se recibe;
 *                             obligatoria (Req 32.1).
 * @param cantidadRecibida     cantidad recibida ahora; estrictamente positiva.
 */
public record RegistrarPartidaRecepcionCommand(
        UUID partidaOrdenCompraId,
        BigDecimal cantidadRecibida) {
}
