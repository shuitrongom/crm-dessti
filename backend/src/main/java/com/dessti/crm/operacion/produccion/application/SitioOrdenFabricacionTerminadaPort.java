package com.dessti.crm.operacion.produccion.application;

import java.util.UUID;

/**
 * Puerto de <strong>solo lectura</strong> del Nucleo {@code produccion} que expone,
 * <strong>por Sitio</strong>, la fase de produccion del avance de un Proyecto
 * (Decision D5-b, &sect;A3): si el Sitio tiene una {@code Orden_Fabricacion}
 * terminada que lo respalda. Lo consume el Nucleo {@code proyecto} (via
 * {@code AvanceProduccionAdapter}) para computar la unica bandera de produccion del
 * {@code AvanceFasesSitio} en giros genericos, sin depender de los puertos de fase
 * del vertical de anuncios.
 *
 * <p>Se distingue de {@link OrdenFabricacionTerminadaPort} —que responde por
 * identificador de Orden_Fabricacion (guarda de creacion de la OTI, Req 19.2)— en
 * que esta consulta se expresa <strong>por Sitio</strong>, que es la unidad de
 * avance del Proyecto (Req 3.2).</p>
 *
 * <h2>Nota de modelado (Nucleo sin vinculo OF&rarr;Sitio)</h2>
 * <p>En el Nucleo la {@code Orden_Fabricacion} se vincula a una Cotizacion/Cliente y
 * <strong>no</strong> tiene columna {@code sitio_id}. Por tanto el adaptador del
 * Nucleo no puede afirmar, por si solo, que un Sitio concreto tenga una OF terminada
 * y devuelve {@code false} de forma <em>deny-safe</em> (Sitio sin produccion
 * confirmada). El vertical que si conozca ese vinculo (p. ej. anuncios, a traves de
 * la Orden_Trabajo_Instalacion) provee su propia composicion del avance. Publicar el
 * puerto ahora deja el punto de extension listo para cuando exista un vinculo
 * OF&rarr;Sitio explicito, sin acoplar el Nucleo a ningun vertical.</p>
 */
public interface SitioOrdenFabricacionTerminadaPort {

    /**
     * Indica si el Sitio dado tiene una {@code Orden_Fabricacion} terminada que lo
     * respalda en el tenant vigente (Req 3.2). Corresponde a la fase de produccion
     * del avance del Proyecto.
     *
     * @param sitioId identificador del Sitio a verificar.
     * @return {@code true} si el Sitio tiene una Orden_Fabricacion terminada que lo
     *         respalda; {@code false} en caso contrario (o si el vinculo no es
     *         resoluble en el Nucleo).
     */
    boolean sitioTieneOrdenFabricacionTerminada(UUID sitioId);
}
