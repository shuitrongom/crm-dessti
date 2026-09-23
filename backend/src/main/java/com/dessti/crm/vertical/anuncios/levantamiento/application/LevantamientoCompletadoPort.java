package com.dessti.crm.vertical.anuncios.levantamiento.application;

import java.util.UUID;

/**
 * Puerto de consulta que expone la guarda de programacion de instalacion del
 * Req 16.5: si un Sitio tiene un {@code Levantamiento_Sitio} en estado
 * {@code completado}. Lo <strong>consumira</strong> el bloque 22 (instalacion,
 * tarea 22.1): la programacion de la instalacion de un Sitio se permite solo si
 * esta consulta devuelve {@code true} (Req 16.5, 19).
 *
 * <p>Publicarlo como puerto propio del modulo de produccion permite que el
 * submodulo de instalacion dependa de una <strong>interfaz estable</strong> —sin
 * acoplarse a la persistencia del Levantamiento— manteniendo la arquitectura
 * hexagonal. El adaptador {@code LevantamientoCompletadoAdapter} lo implementa
 * delegando en el {@code LevantamientoSitioRepository}, acotado al tenant vigente
 * (Req 23). Sigue el patron de {@code PruebaDisenoAprobadaPort} del submodulo de
 * Pruebas de Diseno.</p>
 */
public interface LevantamientoCompletadoPort {

    /**
     * Indica si el Sitio dado tiene al menos un Levantamiento_Sitio en estado
     * {@code completado} en el tenant vigente (Req 16.5). Es la guarda que habilita
     * la programacion de la instalacion de un Sitio.
     *
     * @param sitioId identificador del Sitio a verificar.
     * @return {@code true} si el Sitio tiene un Levantamiento completado.
     */
    boolean sitioTieneLevantamientoCompletado(UUID sitioId);

    /**
     * Indica si el Levantamiento_Sitio identificado esta en estado
     * {@code completado} en el tenant vigente (Req 16.5). Variante por
     * identificador de Levantamiento, util cuando la guarda se expresa sobre un
     * Levantamiento concreto.
     *
     * @param levantamientoId identificador del Levantamiento_Sitio a verificar.
     * @return {@code true} si el Levantamiento existe (en el tenant) y esta completado.
     */
    boolean estaCompletado(UUID levantamientoId);
}
