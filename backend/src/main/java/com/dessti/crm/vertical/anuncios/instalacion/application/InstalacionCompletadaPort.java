package com.dessti.crm.vertical.anuncios.instalacion.application;

import java.util.UUID;

/**
 * Puerto de <strong>solo lectura</strong> del submodulo instalacion que expone,
 * por Sitio, dos hechos derivados de las Ordenes de Trabajo de Instalacion (OTI)
 * necesarios para el avance consolidado del Proyecto (Req 21.3, 21.4). Lo consume
 * el submodulo proyecto (via {@code AvanceSitioPort}/{@code AvanceSitioAdapter}).
 *
 * <p>Publicarlo como puerto propio mantiene la arquitectura hexagonal: el submodulo
 * proyecto depende de esta <strong>interfaz estable</strong> y no de la persistencia
 * de la OTI. El adaptador {@code InstalacionCompletadaAdapter} lo implementa
 * delegando en el {@code OrdenTrabajoInstalacionRepository}, acotado al tenant
 * vigente (Req 23). Sigue el patron de {@code PermisoAprobadoPort} y
 * {@code LevantamientoCompletadoPort}.</p>
 */
public interface InstalacionCompletadaPort {

    /**
     * Indica si el Sitio dado tiene al menos una Orden_Trabajo_Instalacion en estado
     * {@code completada} en el tenant vigente (Req 19.5). Corresponde a la fase
     * Orden_Trabajo_Instalacion cubierta del Req 21.3.
     *
     * @param sitioId identificador del Sitio a verificar.
     * @return {@code true} si el Sitio tiene una instalacion completada.
     */
    boolean sitioTieneInstalacionCompletada(UUID sitioId);

    /**
     * Indica si el Sitio dado tiene una Orden_Fabricacion terminada que lo respalda,
     * <em>derivado</em> de la existencia de al menos una Orden_Trabajo_Instalacion
     * para el Sitio en el tenant vigente (Req 21.3).
     *
     * <p><strong>Justificacion del modelado (deliberada y defendible):</strong> la
     * Orden_Fabricacion se vincula a una Cotizacion, no directamente a un Sitio, y
     * no existe una columna {@code of.sitio_id}. Para no <em>inventar</em> esa
     * columna, la fase "Orden_Fabricacion terminada para este Sitio" se modela a
     * traves de la OTI: una OTI solo puede programarse a partir de una
     * Orden_Fabricacion terminada (Req 19.1/19.2) y la OTI si conoce su
     * {@code sitio_id}. Por tanto, la existencia de una OTI para el Sitio (en
     * cualquier estado) <strong>implica</strong> que una Orden_Fabricacion terminada
     * la respalda. Esta derivacion evita duplicar el vinculo OF-&gt;Sitio y mantiene
     * la unica fuente de verdad en el flujo OF terminada -&gt; OTI.</p>
     *
     * @param sitioId identificador del Sitio a verificar.
     * @return {@code true} si el Sitio tiene una Orden_Fabricacion terminada que lo
     *         respalda (existe una OTI para el Sitio).
     */
    boolean sitioTieneOrdenFabricacionRespaldada(UUID sitioId);
}