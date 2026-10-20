package com.dessti.crm.facturacion.factura.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida del modulo de facturacion-cfdi para consultar los dias de
 * credito del Cliente receptor al derivar la fecha de vencimiento de la
 * Cuenta_Por_Cobrar al timbrar (Req 36.1, 36.5). Aisla a la aplicacion de
 * facturacion de la persistencia del modulo comercial-crm, siguiendo el principio
 * de vistas minimas de {@code CotizacionParaFactura}.
 *
 * <p>Devuelve un {@link Optional} en lugar del Cliente: vacio cuando el Cliente no
 * es accesible en el tenant vigente (otro tenant o inexistente). La decision de
 * degradar a contado queda explicitamente en el servicio, no en el adaptador.</p>
 */
public interface ClienteCreditoPort {

    /**
     * Devuelve los dias de credito del Cliente del tenant vigente (Req 36.5).
     *
     * @param clienteId identificador del Cliente receptor; {@code null} produce vacio.
     * @return los dias de credito del Cliente, o vacio si no es accesible (otro
     *         tenant o inexistente); el servicio degrada a contado.
     */
    Optional<Integer> diasCreditoDe(UUID clienteId);
}
