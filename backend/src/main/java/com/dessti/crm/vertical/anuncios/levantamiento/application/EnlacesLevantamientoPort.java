package com.dessti.crm.vertical.anuncios.levantamiento.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la <strong>existencia en el tenant vigente</strong>
 * de los vinculos opcionales de un Levantamiento_Sitio: la Cotizacion y la
 * Orden_Fabricacion (Req 16.2). Se usa al crear un Levantamiento para rechazar con
 * 404 un vinculo a un recurso inexistente o de otro tenant (Req 23.3), sin acoplar
 * la aplicacion del Levantamiento a la persistencia de esos agregados.
 *
 * <p>El adaptador {@code EnlacesLevantamientoAdapter} lo implementa delegando en el
 * puerto de consulta del Nucleo
 * {@code com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort}
 * (para la Cotizacion, Req 10.3, 10.5) y en el
 * {@code OrdenFabricacionRepository} (mismo vertical de anuncios), ambos acotados
 * al tenant vigente por el filtro global y la RLS (Req 23). Sigue el patron de
 * puerto/adaptador de {@code CotizacionParaFabricacionPort}.</p>
 */
public interface EnlacesLevantamientoPort {

    /**
     * Indica si existe una Cotizacion con el identificador dado en el tenant
     * vigente (Req 16.2, 23.3).
     *
     * @param cotizacionId identificador de la Cotizacion a verificar.
     * @return {@code true} si la Cotizacion existe y es accesible en el tenant.
     */
    boolean existeCotizacion(UUID cotizacionId);

    /**
     * Indica si existe una Orden_Fabricacion con el identificador dado en el tenant
     * vigente (Req 16.2, 23.3).
     *
     * @param ordenFabricacionId identificador de la Orden_Fabricacion a verificar.
     * @return {@code true} si la Orden_Fabricacion existe y es accesible en el tenant.
     */
    boolean existeOrdenFabricacion(UUID ordenFabricacionId);
}
