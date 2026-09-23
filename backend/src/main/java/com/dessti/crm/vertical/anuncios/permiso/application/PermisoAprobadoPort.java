package com.dessti.crm.vertical.anuncios.permiso.application;

import java.util.UUID;

/**
 * Puerto de consulta que expone la guarda de programacion de instalacion del
 * Req 17.4: si un Sitio tiene un {@code Permiso_Instalacion} requerido en estado
 * {@code aprobado}. Lo <strong>consumira</strong> el bloque 22 (instalacion, tarea
 * 22.1): la programacion de la instalacion de un Sitio se permite solo si esta
 * consulta devuelve {@code true} (Req 17.4, 19.3).
 *
 * <p>Publicarlo como puerto propio del modulo de produccion permite que el
 * submodulo de instalacion dependa de una <strong>interfaz estable</strong> —sin
 * acoplarse a la persistencia del permiso— manteniendo la arquitectura hexagonal.
 * El adaptador {@code PermisoAprobadoAdapter} lo implementa delegando en el
 * {@code PermisoInstalacionRepository}, acotado al tenant vigente (Req 23). Sigue el
 * patron de {@code LevantamientoCompletadoPort} del submodulo de levantamiento.</p>
 */
public interface PermisoAprobadoPort {

    /**
     * Indica si el Sitio dado tiene al menos un Permiso_Instalacion en estado
     * {@code aprobado} en el tenant vigente (Req 17.4). Es la guarda que habilita la
     * programacion de la instalacion de un Sitio.
     *
     * @param sitioId identificador del Sitio a verificar.
     * @return {@code true} si el Sitio tiene un permiso aprobado.
     */
    boolean sitioTienePermisoAprobado(UUID sitioId);

    /**
     * Indica si el Permiso_Instalacion identificado esta en estado {@code aprobado}
     * en el tenant vigente (Req 17.4). Variante por identificador de permiso, util
     * cuando la guarda se expresa sobre un permiso concreto.
     *
     * @param permisoId identificador del Permiso_Instalacion a verificar.
     * @return {@code true} si el permiso existe (en el tenant) y esta aprobado.
     */
    boolean estaAprobado(UUID permisoId);
}
