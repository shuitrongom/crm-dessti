package com.dessti.crm.operacion.proyecto.domain;

/**
 * Estado consolidado de un Proyecto multi-sitio de giro <strong>generico</strong>
 * (no anuncios), derivado de la {@link FaseSitioGenerica} de todos sus Sitios
 * (Req 3.2, 21.4). Es el analogo generico de {@link EstadoConsolidadoProyecto} pero
 * sobre el avance materializado y editable de {@code avance_sitio} (V78), no sobre
 * las cuatro fases derivadas de anuncios.
 *
 * <p>Regla de derivacion (ver {@link DerivacionEstadoMultisitio}): sin Sitios,
 * {@link #SIN_SITIOS}; si todos los Sitios estan {@code ENTREGADO},
 * {@link #COMPLETADO}; en otro caso, la primera etapa del despliegue que aun no
 * cubren todos los Sitios, en orden Preparacion &rarr; Instalacion &rarr; Entrega.</p>
 */
public enum EstadoConsolidadoMultisitio {

    /** El Proyecto aun no tiene Sitios. */
    SIN_SITIOS("sin_sitios"),

    /** Al menos un Sitio sigue en fase {@code pendiente} (sin iniciar preparacion). */
    EN_PREPARACION("en_preparacion"),

    /** Todos iniciaron pero al menos un Sitio no ha llegado a instalacion. */
    EN_INSTALACION("en_instalacion"),

    /** Todos en instalacion o mas, pero al menos un Sitio no esta entregado. */
    EN_ENTREGA("en_entrega"),

    /** Todos los Sitios estan entregados. */
    COMPLETADO("completado");

    private final String valorBd;

    EstadoConsolidadoMultisitio(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII en minusculas del estado consolidado, para exponerlo en los DTOs.
     *
     * @return la etiqueta del estado.
     */
    public String valorBd() {
        return valorBd;
    }
}
