package com.dessti.crm.operacion.proyecto.domain;

import java.util.List;

/**
 * Funcion pura de dominio que deriva el {@link EstadoConsolidadoMultisitio} de un
 * Proyecto multi-sitio de giro generico a partir de la {@link FaseSitioGenerica} de
 * cada uno de sus Sitios (Req 3.2, 3.6, 21.4). Es el analogo generico de
 * {@link DerivacionEstadoProyecto} y NO comparte estado ni logica con ella, de modo
 * que el pipeline de anuncios permanece intacto.
 *
 * <p>Sin efectos secundarios y determinista: recibe la lista de fases (una por
 * Sitio) y devuelve el estado consolidado, por lo que es comprobable unitariamente
 * sin infraestructura.</p>
 *
 * <p>Regla (el despliegue avanza sitio por sitio; el consolidado refleja al Sitio
 * mas rezagado):</p>
 * <ul>
 *   <li>lista vacia &rarr; {@link EstadoConsolidadoMultisitio#SIN_SITIOS};</li>
 *   <li>algun Sitio en {@code PENDIENTE} &rarr; {@code EN_PREPARACION};</li>
 *   <li>si no, algun Sitio en {@code EN_PREPARACION} &rarr; {@code EN_INSTALACION};</li>
 *   <li>si no, algun Sitio en {@code EN_INSTALACION} &rarr; {@code EN_ENTREGA};</li>
 *   <li>todos en {@code ENTREGADO} &rarr; {@code COMPLETADO}.</li>
 * </ul>
 */
public final class DerivacionEstadoMultisitio {

    private DerivacionEstadoMultisitio() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Deriva el estado consolidado del Proyecto multi-sitio a partir de las fases de
     * sus Sitios (Req 3.2). Funcion pura y determinista (Req 3.6).
     *
     * @param fases lista con la fase de cada Sitio del Proyecto; nunca {@code null}
     *              (una lista vacia significa Proyecto sin Sitios). Un elemento
     *              {@code null} se interpreta como {@link FaseSitioGenerica#PENDIENTE}
     *              (Sitio sin avance materializado todavia).
     * @return el estado consolidado derivado.
     * @throws NullPointerException si {@code fases} es {@code null}.
     */
    public static EstadoConsolidadoMultisitio derivar(List<FaseSitioGenerica> fases) {
        if (fases == null) {
            throw new NullPointerException("La lista de fases de los Sitios es obligatoria.");
        }
        if (fases.isEmpty()) {
            return EstadoConsolidadoMultisitio.SIN_SITIOS;
        }

        boolean algunPendiente = false;
        boolean algunPreparacion = false;
        boolean algunInstalacion = false;

        for (FaseSitioGenerica fase : fases) {
            // Un Sitio sin fila de avance se considera 'pendiente' (default logico).
            FaseSitioGenerica efectiva = (fase == null) ? FaseSitioGenerica.PENDIENTE : fase;
            switch (efectiva) {
                case PENDIENTE -> algunPendiente = true;
                case EN_PREPARACION -> algunPreparacion = true;
                case EN_INSTALACION -> algunInstalacion = true;
                case ENTREGADO -> {
                    // No rezaga el consolidado.
                }
            }
        }

        // Etapa mas rezagada, en orden operativo del despliegue.
        if (algunPendiente) {
            return EstadoConsolidadoMultisitio.EN_PREPARACION;
        }
        if (algunPreparacion) {
            return EstadoConsolidadoMultisitio.EN_INSTALACION;
        }
        if (algunInstalacion) {
            return EstadoConsolidadoMultisitio.EN_ENTREGA;
        }
        return EstadoConsolidadoMultisitio.COMPLETADO;
    }
}
