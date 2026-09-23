package com.dessti.crm.operacion.produccion.application;

import java.util.UUID;

/**
 * Puerto de consulta (del Nucleo) que expone la precondicion de diseno para generar
 * una Orden_Fabricacion desde una Cotizacion: si esa Cotizacion tiene al menos una
 * Prueba_Diseno en estado {@code aprobada} (Req 15.5). Lo <strong>consume</strong>
 * {@code ServicioOrdenesFabricacion.generar} (Property 7).
 *
 * <p>Vive en el Nucleo ({@code operacion.produccion.application}) junto a su
 * consumidor, de modo que el Nucleo NO dependa en compilacion del vertical de
 * anuncios (Req 10.5): el enchufe es por inyeccion de la interfaz. El adaptador
 * {@code PruebaDisenoAprobadaAdapter} del vertical de anuncios la implementa
 * delegando en el {@code PruebaDisenoRepository}, acotado al tenant vigente
 * (Req 23). Sigue el patron de {@link OrdenFabricacionTerminadaPort}, que tambien
 * reside en el Nucleo y es implementado desde fuera.</p>
 */
public interface PruebaDisenoAprobadaPort {

    /**
     * Indica si la Cotizacion dada tiene al menos una Prueba_Diseno en estado
     * {@code aprobada} en el tenant vigente (Req 15.5).
     *
     * @param cotizacionId identificador de la Cotizacion a verificar.
     * @return {@code true} si existe alguna Prueba_Diseno aprobada para la Cotizacion.
     */
    boolean tieneAprobadaPorCotizacion(UUID cotizacionId);
}