package com.dessti.crm.operacion.proyecto.application;

import com.dessti.crm.operacion.proyecto.domain.AvanceFasesSitio;
import com.dessti.crm.operacion.proyecto.domain.Sitio;

/**
 * DTO de salida que combina un {@link Sitio} con su avance en las cuatro fases de
 * instalacion (Req 21.3, 21.4). Forma parte del detalle de un Proyecto consultado
 * ({@link ProyectoDto}) para mostrar, por Sitio, el estado de cada fase.
 *
 * <p>Las cuatro banderas de fase se exponen aplanadas (en lugar de anidar el
 * {@link AvanceFasesSitio}) para un contrato JSON estable y directo.</p>
 *
 * @param sitio                          datos del Sitio (Req 21.2).
 * @param tieneLevantamientoCompletado   fase Levantamiento_Sitio cubierta (Req 16.4).
 * @param tienePermisoAprobado           fase Permiso_Instalacion cubierta (Req 17.4).
 * @param tieneOrdenFabricacionTerminada fase Orden_Fabricacion cubierta (Req 19.1/19.2).
 * @param tieneInstalacionCompletada     fase Orden_Trabajo_Instalacion cubierta (Req 19.5).
 */
public record SitioAvanceDto(
        SitioDto sitio,
        boolean tieneLevantamientoCompletado,
        boolean tienePermisoAprobado,
        boolean tieneOrdenFabricacionTerminada,
        boolean tieneInstalacionCompletada) {

    /**
     * Combina la entidad {@link Sitio} con su {@link AvanceFasesSitio} en el DTO de
     * salida.
     *
     * @param sitio  entidad del Sitio.
     * @param avance avance del Sitio en las cuatro fases.
     * @return el DTO combinado.
     */
    public static SitioAvanceDto de(Sitio sitio, AvanceFasesSitio avance) {
        return new SitioAvanceDto(
                SitioDto.de(sitio),
                avance.tieneLevantamientoCompletado(),
                avance.tienePermisoAprobado(),
                avance.tieneOrdenFabricacionTerminada(),
                avance.tieneInstalacionCompletada());
    }
}
