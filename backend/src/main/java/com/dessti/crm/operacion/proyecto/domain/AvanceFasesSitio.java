package com.dessti.crm.operacion.proyecto.domain;

/**
 * Objeto de valor <strong>puro</strong> del dominio que representa el avance de un
 * Sitio en las cuatro fases de instalacion del Req 21.3, expresado como cuatro
 * indicadores booleanos (fase alcanzada/completada). Es inmutable y no depende de
 * Spring ni de JPA, por lo que puede usarse en la funcion pura de derivacion del
 * estado consolidado ({@link DerivacionEstadoProyecto}) y en pruebas unitarias o
 * de propiedades sin infraestructura.
 *
 * <p>Se modela con cuatro booleanos —en lugar de un enum por fase— para que la
 * derivacion consolidada del Req 21.4 sea una funcion pura y trivial de razonar:
 * cada bandera responde "¿la fase esta cubierta para este Sitio?".</p>
 *
 * <p>Aunque lo <em>produce</em> la capa de aplicacion (a traves del
 * {@code AvanceSitioPort}, que consulta otros modulos), se ubica en el
 * <strong>dominio</strong> porque es un concepto de negocio puro que la funcion de
 * derivacion del dominio necesita, evitando que el dominio dependa de la capa de
 * aplicacion (regla de dependencias hexagonal: aplicacion y adaptadores pueden
 * depender del dominio, nunca al reves).</p>
 *
 * <h2>Semantica de las fases (Req 21.3)</h2>
 * <ul>
 *   <li>{@code tieneLevantamientoCompletado}: el Sitio tiene un Levantamiento_Sitio
 *       en estado {@code completado} (Req 16.4).</li>
 *   <li>{@code tienePermisoAprobado}: el Sitio tiene un Permiso_Instalacion en
 *       estado {@code aprobado} (Req 17.4).</li>
 *   <li>{@code tieneOrdenFabricacionTerminada}: existe una Orden_Trabajo_Instalacion
 *       para el Sitio, lo que <em>implica</em> una Orden_Fabricacion terminada que
 *       la respalda (una OTI solo se programa desde una OF terminada, Req 19.1/19.2).
 *       Ver la justificacion en {@code InstalacionCompletadaPort}.</li>
 *   <li>{@code tieneInstalacionCompletada}: el Sitio tiene una
 *       Orden_Trabajo_Instalacion en estado {@code completada} (Req 19.5).</li>
 * </ul>
 *
 * @param tieneLevantamientoCompletado   fase Levantamiento_Sitio cubierta (Req 16.4).
 * @param tienePermisoAprobado           fase Permiso_Instalacion cubierta (Req 17.4).
 * @param tieneOrdenFabricacionTerminada fase Orden_Fabricacion cubierta (Req 19.1/19.2,
 *                                       derivada de la existencia de una OTI).
 * @param tieneInstalacionCompletada     fase Orden_Trabajo_Instalacion cubierta (Req 19.5).
 */
public record AvanceFasesSitio(
        boolean tieneLevantamientoCompletado,
        boolean tienePermisoAprobado,
        boolean tieneOrdenFabricacionTerminada,
        boolean tieneInstalacionCompletada) {
}
