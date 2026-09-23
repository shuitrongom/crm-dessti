package com.dessti.crm.operacion.produccion.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de consulta que expone la guarda de creacion de una
 * Orden_Trabajo_Instalacion del Req 19.1/19.2: una OTI solo puede crearse a partir
 * de una {@code Orden_Fabricacion} en estado {@code terminada}. Lo
 * <strong>consumira</strong> el submodulo de instalacion (bloque 22, tarea 22.1):
 * la programacion de la instalacion se permite solo si {@link #estaTerminada(UUID)}
 * devuelve {@code true} (Req 19.2).
 *
 * <p>Publicarlo como puerto propio del vertical de anuncios permite que el
 * submodulo de instalacion dependa de una <strong>interfaz estable</strong> —sin
 * acoplarse a la persistencia de la Orden_Fabricacion— manteniendo la arquitectura
 * hexagonal. El adaptador {@code OrdenFabricacionTerminadaAdapter} lo implementa
 * delegando en el {@code OrdenFabricacionRepository}, acotado al tenant vigente
 * (Req 23). Sigue el patron de {@code PermisoAprobadoPort}/{@code LevantamientoCompletadoPort}.</p>
 */
public interface OrdenFabricacionTerminadaPort {

    /**
     * Indica si la Orden_Fabricacion identificada esta en estado {@code terminada}
     * en el tenant vigente (Req 19.2). Es la guarda que habilita la creacion de una
     * Orden_Trabajo_Instalacion a partir de ella.
     *
     * @param ordenFabricacionId identificador de la Orden_Fabricacion a verificar.
     * @return {@code true} si la Orden_Fabricacion existe (en el tenant) y esta
     *         terminada.
     */
    boolean estaTerminada(UUID ordenFabricacionId);

    /**
     * Recupera el Cliente de la Orden_Fabricacion del tenant vigente, para
     * denormalizarlo en la Orden_Trabajo_Instalacion y habilitar el filtro del
     * listado por Cliente (Req 19.7). Devuelve {@link Optional#empty()} si la
     * Orden_Fabricacion no existe o pertenece a otro tenant (Req 23.3).
     *
     * @param ordenFabricacionId identificador de la Orden_Fabricacion.
     * @return el identificador del Cliente, o vacio si no es accesible.
     */
    Optional<UUID> clienteDeOrden(UUID ordenFabricacionId);
}
