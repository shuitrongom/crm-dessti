package com.dessti.crm.facturacion.notacredito.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vista minima de una {@code Factura} referenciada por una Nota de Credito
 * (Req 37.1, 37.2). Objeto de solo lectura que expone el
 * {@link FacturaReferenciadaPort}.
 *
 * @param facturaId identificador de la Factura.
 * @param estado    etiqueta ASCII del estado de la Factura (por ejemplo {@code 'timbrada'}).
 * @param clienteId Cliente al que se emitio la Factura (Req 37).
 * @param total     total de la Factura, base del saldo disponible (Req 37.2).
 */
public record FacturaReferenciada(UUID facturaId, String estado, UUID clienteId, BigDecimal total) {

    /** Etiqueta del estado {@code timbrada} de una Factura (Req 37.1). */
    public static final String ESTADO_TIMBRADA = "timbrada";

    /**
     * Indica si la Factura esta {@code timbrada}, precondicion para emitir una Nota
     * de Credito que la referencie (Req 37.1). Insensible a mayusculas/espacios.
     *
     * @return {@code true} si la Factura esta timbrada.
     */
    public boolean estaTimbrada() {
        return estado != null && ESTADO_TIMBRADA.equalsIgnoreCase(estado.strip());
    }
}
