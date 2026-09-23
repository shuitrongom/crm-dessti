package com.dessti.crm.operacion.produccion.application;

import java.util.UUID;

/**
 * Vista minima de una {@code Cotizacion} necesaria para decidir y ejecutar la
 * generacion de una Orden_Fabricacion (Req 7.1, 7.2, 7.9). Es un objeto de solo
 * lectura, libre de dependencias de persistencia, que expone el
 * {@link CotizacionParaFabricacionPort}.
 *
 * <p>Contiene unicamente lo que la regla de negocio requiere: el estado de la
 * Cotizacion (para verificar que esta {@code aprobada}, Req 7.2) y el
 * {@code clienteId} (que se denormaliza en la Orden_Fabricacion para el filtro del
 * listado por Cliente, Req 7.9). No expone el resto del modelo de la Cotizacion,
 * manteniendo el vertical de anuncios desacoplado del Nucleo comercial.</p>
 *
 * @param cotizacionId identificador de la Cotizacion.
 * @param estado       etiqueta ASCII del estado de la Cotizacion (por ejemplo
 *                     {@code 'aprobada'}); coincide con {@code EstadoCotizacion#valorBd()}.
 * @param clienteId    Cliente al que pertenece la Cotizacion (Req 7.9).
 */
public record CotizacionParaFabricacion(UUID cotizacionId, String estado, UUID clienteId) {

    /** Etiqueta del estado {@code aprobada} de una Cotizacion (Req 7.2). */
    public static final String ESTADO_APROBADA = "aprobada";

    /**
     * Indica si la Cotizacion esta en estado {@code aprobada}, unica precondicion
     * de estado para generar una Orden_Fabricacion (Req 7.1, 7.2). La comparacion
     * es insensible a mayusculas/espacios.
     *
     * @return {@code true} si la Cotizacion esta aprobada.
     */
    public boolean estaAprobada() {
        return estado != null && ESTADO_APROBADA.equalsIgnoreCase(estado.strip());
    }
}
