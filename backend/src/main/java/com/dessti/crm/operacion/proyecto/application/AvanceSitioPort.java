package com.dessti.crm.operacion.proyecto.application;

import java.util.UUID;

import com.dessti.crm.operacion.proyecto.domain.AvanceFasesSitio;

/**
 * Puerto de <strong>solo lectura</strong> del submodulo proyecto que expone el
 * avance de un Sitio en las cuatro fases de instalacion del Req 21.3
 * (Levantamiento_Sitio, Permiso_Instalacion, Orden_Fabricacion y
 * Orden_Trabajo_Instalacion). Lo consume {@code ServicioProyectos} para computar,
 * por Sitio, un {@link AvanceFasesSitio} y, a partir de todos ellos, derivar el
 * estado consolidado del Proyecto (Req 21.4).
 *
 * <p>Publicarlo como puerto propio mantiene la arquitectura hexagonal: el
 * submodulo proyecto depende de esta <strong>interfaz estable</strong> y no de la
 * persistencia de los otros modulos. Tiene <strong>dos</strong> implementaciones
 * (Decision D5-b): {@code AvanceProduccionAdapter} (Nucleo) compone solo la fase de
 * produccion via {@code SitioOrdenFabricacionTerminadaPort} —para giros genericos—,
 * y {@code AvanceSitioAnunciosAdapter} (vertical de anuncios) compone las cuatro
 * fases delegando en {@code LevantamientoCompletadoPort},
 * {@code PermisoAprobadoPort} e {@code InstalacionCompletadaPort}, todos acotados al
 * tenant vigente (Req 23). {@code ServicioProyectos} selecciona el adaptador segun
 * el {@code PerfilFasesGiro} del tenant, evitando que un Proyecto generico dependa
 * de los puertos de anuncios.</p>
 */
public interface AvanceSitioPort {

    /**
     * Calcula el avance del Sitio dado en las cuatro fases de instalacion
     * (Req 21.3), consultando los modulos correspondientes en el tenant vigente.
     *
     * @param sitioId identificador del Sitio; obligatorio.
     * @return el {@link AvanceFasesSitio} con el estado de cada fase para el Sitio;
     *         nunca {@code null} (un {@code sitioId} nulo produce todas las fases
     *         en {@code false}).
     */
    AvanceFasesSitio avanceDe(UUID sitioId);
}
