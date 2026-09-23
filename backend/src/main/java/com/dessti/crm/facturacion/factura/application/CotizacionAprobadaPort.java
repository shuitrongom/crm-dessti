package com.dessti.crm.facturacion.factura.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida que expone la vista minima de una {@code Cotizacion} necesaria
 * para emitir una Factura a partir de ella: su estado, su {@code clienteId} y su
 * importe base (Req 34.1). Se define en el modulo de facturacion para que este
 * dependa de una <strong>interfaz estable</strong> y no de la persistencia del
 * modulo comercial, preservando la arquitectura hexagonal.
 *
 * <p>El adaptador {@code CotizacionAprobadaAdapter} lo implementa delegando en el
 * {@code CotizacionRepository} (modulo comercial-crm), acotado al tenant vigente
 * (Req 23). Sigue el mismo patron que {@code CotizacionParaFabricacionPort} del
 * modulo de produccion.</p>
 */
public interface CotizacionAprobadaPort {

    /**
     * Recupera la vista de facturacion de una Cotizacion del tenant vigente
     * (estado + clienteId + importe base), o {@link Optional#empty()} si la
     * Cotizacion no existe o pertenece a otro tenant (Req 23.3).
     *
     * @param cotizacionId identificador de la Cotizacion a consultar.
     * @return la vista de facturacion de la Cotizacion, o vacio si no es accesible.
     */
    Optional<CotizacionParaFactura> buscarParaFactura(UUID cotizacionId);
}
