package com.dessti.crm.facturacion.notacredito.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida que expone la vista minima de una {@code Factura} necesaria para
 * emitir una Nota de Credito que la referencie: si esta {@code timbrada}, su
 * Cliente y su total (Req 37.1, 37.2). Se define en el submodulo de notas de
 * credito para desacoplarlo de la persistencia de la Factura, aunque ambos vivan
 * en el mismo modulo de facturacion.
 *
 * <p>El adaptador {@code FacturaReferenciadaAdapter} lo implementa delegando en el
 * {@code FacturaRepository}, acotado al tenant vigente (Req 23).</p>
 */
public interface FacturaReferenciadaPort {

    /**
     * Recupera la vista de la Factura referenciada del tenant vigente (estado +
     * clienteId + total), o {@link Optional#empty()} si no existe o pertenece a otro
     * tenant (Req 23.3).
     *
     * @param facturaId identificador de la Factura referenciada.
     * @return la vista de la Factura, o vacio si no es accesible.
     */
    Optional<FacturaReferenciada> buscar(UUID facturaId);
}
