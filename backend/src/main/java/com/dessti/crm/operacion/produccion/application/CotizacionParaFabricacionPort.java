package com.dessti.crm.operacion.produccion.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida que expone la vista minima de una {@code Cotizacion} necesaria
 * para generar una Orden_Fabricacion: su estado y su {@code clienteId} (Req 7.1,
 * 7.2, 7.9).
 *
 * <p>Se define en el vertical de anuncios para que este dependa de una
 * <strong>interfaz estable</strong> y no de la persistencia del Nucleo comercial,
 * preservando la arquitectura hexagonal. El adaptador
 * {@code CotizacionParaFabricacionAdapter} lo implementa delegando en el puerto de
 * consulta del Nucleo
 * {@code com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort}
 * (Req 10.3, 10.5), acotado al tenant vigente (Req 23). Sigue el mismo patron de
 * puerto/adaptador que {@code CotizacionExistentePort} del submodulo de Pruebas de
 * Diseno.</p>
 */
public interface CotizacionParaFabricacionPort {

    /**
     * Recupera la vista de fabricacion de una Cotizacion del tenant vigente
     * (estado + clienteId), o {@link Optional#empty()} si la Cotizacion no existe
     * o pertenece a otro tenant (Req 23.3). El aislamiento por tenant lo garantizan
     * el filtro global de Hibernate y la RLS (Req 23).
     *
     * @param cotizacionId identificador de la Cotizacion a consultar.
     * @return la vista de fabricacion de la Cotizacion, o vacio si no es accesible.
     */
    Optional<CotizacionParaFabricacion> buscarParaFabricacion(UUID cotizacionId);
}
