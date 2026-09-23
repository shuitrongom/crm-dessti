package com.dessti.crm.operacion.proyecto.domain;

/**
 * Estado consolidado de un Proyecto (Req 21.4), <strong>derivado</strong> del
 * avance de sus Sitios en las cuatro fases de instalacion (Req 21.3). No se
 * persiste: es el resultado de la funcion pura {@link DerivacionEstadoProyecto},
 * calculada en tiempo de consulta a partir de los {@link AvanceFasesSitio} de los
 * Sitios del Proyecto.
 *
 * <h2>Valores</h2>
 * <ul>
 *   <li>{@link #SIN_SITIOS}: el Proyecto aun no tiene Sitios (Req 21.1 sin Req 21.2).</li>
 *   <li>{@link #EN_LEVANTAMIENTO}: al menos un Sitio no tiene todavia su
 *       Levantamiento_Sitio completado (fase mas temprana pendiente).</li>
 *   <li>{@link #EN_TRAMITE_PERMISOS}: todos los Sitios tienen el Levantamiento
 *       completado, pero al menos uno no tiene aun el Permiso_Instalacion aprobado.</li>
 *   <li>{@link #EN_PRODUCCION}: todos los Sitios tienen Levantamiento y Permiso,
 *       pero al menos uno no tiene aun respaldada la Orden_Fabricacion (no existe
 *       Orden_Trabajo_Instalacion para el Sitio).</li>
 *   <li>{@link #EN_INSTALACION}: todos los Sitios estan respaldados por una
 *       Orden_Fabricacion (tienen OTI), pero al menos uno no ha completado aun la
 *       instalacion.</li>
 *   <li>{@link #COMPLETADO}: todos los Sitios tienen la instalacion completada
 *       (Req 19.5).</li>
 * </ul>
 *
 * <p>La etiqueta ASCII en minusculas ({@link #valorBd()}) se usa para exponer el
 * estado en los DTOs de forma coherente con el resto de estados del sistema.</p>
 */
public enum EstadoConsolidadoProyecto {

    /** El Proyecto aun no tiene Sitios (Req 21.1). */
    SIN_SITIOS("sin_sitios"),

    /** Falta completar el Levantamiento_Sitio en al menos un Sitio. */
    EN_LEVANTAMIENTO("en_levantamiento"),

    /** Falta aprobar el Permiso_Instalacion en al menos un Sitio. */
    EN_TRAMITE_PERMISOS("en_tramite_permisos"),

    /** Falta respaldar la Orden_Fabricacion (OTI) en al menos un Sitio. */
    EN_PRODUCCION("en_produccion"),

    /** Falta completar la instalacion en al menos un Sitio. */
    EN_INSTALACION("en_instalacion"),

    /** Todos los Sitios tienen la instalacion completada (Req 19.5). */
    COMPLETADO("completado");

    private final String valorBd;

    EstadoConsolidadoProyecto(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII en minusculas del estado consolidado, usada para exponerlo en
     * los DTOs de salida.
     *
     * @return la etiqueta del estado.
     */
    public String valorBd() {
        return valorBd;
    }
}
