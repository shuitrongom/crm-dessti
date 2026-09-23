package com.dessti.crm.facturacion.factura.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vista minima de una {@code Cotizacion} necesaria para emitir una Factura a
 * partir de ella (Req 34.1). Objeto de solo lectura, libre de dependencias de
 * persistencia, que expone el {@link CotizacionAprobadaPort}.
 *
 * <p>Contiene lo que la regla de negocio requiere: el estado de la Cotizacion
 * (para verificar que esta {@code aprobada}, Req 34.1), el {@code clienteId} (que
 * la Factura denormaliza para el filtro del Req 34.4) y el importe base
 * ({@link #total()}) sobre el que se calcula el IVA/retenciones/total del CFDI
 * (Req 34.2). No expone el resto del modelo de la Cotizacion.</p>
 *
 * @param cotizacionId identificador de la Cotizacion.
 * @param estado       etiqueta ASCII del estado (por ejemplo {@code 'aprobada'}).
 * @param clienteId    Cliente al que pertenece la Cotizacion (Req 34.4).
 * @param total        total de la Cotizacion, base del calculo fiscal (Req 34.2).
 */
public record CotizacionParaFactura(UUID cotizacionId, String estado, UUID clienteId, BigDecimal total) {

    /** Etiqueta del estado {@code aprobada} de una Cotizacion (Req 34.1). */
    public static final String ESTADO_APROBADA = "aprobada";

    /**
     * Indica si la Cotizacion esta en estado {@code aprobada}, precondicion para
     * emitir una Factura a partir de ella (Req 34.1). La comparacion es insensible
     * a mayusculas/espacios.
     *
     * @return {@code true} si la Cotizacion esta aprobada.
     */
    public boolean estaAprobada() {
        return estado != null && ESTADO_APROBADA.equalsIgnoreCase(estado.strip());
    }
}
