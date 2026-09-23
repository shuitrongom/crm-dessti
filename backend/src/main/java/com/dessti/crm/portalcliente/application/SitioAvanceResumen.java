package com.dessti.crm.portalcliente.application;

/**
 * Resumen de un {@code Sitio} combinado con su avance en las cuatro fases de
 * instalacion, tal como lo expone el Portal del Cliente. Definido por el
 * <strong>Nucleo</strong> (el Portal) e implementado por el vertical de anuncios a
 * traves del {@link ResumenProyectosPort} (Req 10.5). Reproduce exactamente la
 * forma del {@code SitioAvanceDto} del vertical (mismos nombres de campo) para
 * preservar el contrato REST del Portal frente al frontend (Req 10.4).
 *
 * @param sitio                          datos del Sitio (Req 21.2).
 * @param tieneLevantamientoCompletado   fase Levantamiento_Sitio cubierta (Req 16.4).
 * @param tienePermisoAprobado           fase Permiso_Instalacion cubierta (Req 17.4).
 * @param tieneOrdenFabricacionTerminada fase Orden_Fabricacion cubierta (Req 19.1/19.2).
 * @param tieneInstalacionCompletada     fase Orden_Trabajo_Instalacion cubierta (Req 19.5).
 */
public record SitioAvanceResumen(
        SitioResumen sitio,
        boolean tieneLevantamientoCompletado,
        boolean tienePermisoAprobado,
        boolean tieneOrdenFabricacionTerminada,
        boolean tieneInstalacionCompletada) {
}
