package com.dessti.crm.facturacion.factura.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida que expone la vista minima de una {@code Orden_Fabricacion}
 * necesaria para emitir una Factura a partir de ella: su {@code clienteId} y el
 * importe base de su Cotizacion de origen (Req 34.1). Permite que el modulo de
 * facturacion emita Facturas desde una Orden_Fabricacion (Req 34.1) sin acoplarse
 * a la persistencia del modulo de produccion.
 *
 * <p>El adaptador {@code OrdenFabricacionParaFacturaAdapter} vive en el
 * <strong>Nucleo de operacion</strong> ({@code operacion.produccion},
 * Req 10.5) e implementa este puerto delegando en el {@code OrdenFabricacionRepository}
 * (para el Cliente y la Cotizacion de origen) y en el puerto de consulta del Nucleo
 * {@code CotizacionConsultaPort} (para el importe base), ambos acotados al tenant
 * vigente (Req 23). Asi el Nucleo (facturacion) solo depende de este puerto y no de
 * las clases del vertical.</p>
 */
public interface OrdenFabricacionParaFacturaPort {

    /**
     * Recupera la vista de facturacion de una Orden_Fabricacion del tenant vigente
     * (clienteId + importe base), o {@link Optional#empty()} si la OF no existe o
     * pertenece a otro tenant (Req 23.3).
     *
     * @param ordenFabricacionId identificador de la Orden_Fabricacion.
     * @return la vista de facturacion de la OF, o vacio si no es accesible.
     */
    Optional<OrdenFabricacionParaFactura> buscarParaFactura(UUID ordenFabricacionId);
}
