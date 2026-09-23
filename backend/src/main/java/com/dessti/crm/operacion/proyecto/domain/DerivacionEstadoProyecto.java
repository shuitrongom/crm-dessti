package com.dessti.crm.operacion.proyecto.domain;

import java.util.List;

/**
 * Funcion <strong>pura</strong> del dominio que deriva el
 * {@link EstadoConsolidadoProyecto} de un Proyecto a partir del avance de sus
 * Sitios (Req 3.2, 3.6). No depende de Spring ni de JPA: recibe un
 * {@link PerfilFasesGiro} (que fases aplican al giro) y la lista de
 * {@link AvanceFasesSitio} (uno por Sitio) y devuelve el estado consolidado, por
 * lo que es completamente determinista y unitariamente comprobable (incluso con
 * pruebas de propiedades en la tarea 1.12).
 *
 * <h2>Regla de derivacion por fases aplicables (Req 3.2, 3.3, 3.4)</h2>
 * <p>El estado consolidado refleja la <strong>primera fase aplicable no cubierta
 * por todos los Sitios</strong>, recorriendo las fases en el orden operativo
 * (Levantamiento -&gt; Permiso -&gt; Produccion -&gt; Instalacion) pero
 * considerando <strong>solo</strong> las contenidas en
 * {@code perfil.fasesAplicables()}:</p>
 * <ol>
 *   <li>Si el Proyecto no tiene Sitios: {@link EstadoConsolidadoProyecto#SIN_SITIOS},
 *       con independencia del perfil/giro (Req 3.4).</li>
 *   <li>Para cada fase aplicable, en orden, si algun Sitio no la cubre se devuelve el
 *       estado consolidado correspondiente a esa fase (mapeo fase -&gt; estado):
 *       Levantamiento -&gt; {@link EstadoConsolidadoProyecto#EN_LEVANTAMIENTO},
 *       Permiso -&gt; {@link EstadoConsolidadoProyecto#EN_TRAMITE_PERMISOS},
 *       Produccion -&gt; {@link EstadoConsolidadoProyecto#EN_PRODUCCION},
 *       Instalacion -&gt; {@link EstadoConsolidadoProyecto#EN_INSTALACION}.</li>
 *   <li>Si <strong>todas</strong> las fases aplicables estan cubiertas por todos los
 *       Sitios: {@link EstadoConsolidadoProyecto#COMPLETADO} (Req 3.3).</li>
 * </ol>
 *
 * <p>Mapeo fase -&gt; bandera de {@link AvanceFasesSitio} (identico a la logica
 * clasica de anuncios, Req 3.5): Levantamiento usa
 * {@code tieneLevantamientoCompletado}, Permiso usa {@code tienePermisoAprobado},
 * Produccion usa {@code tieneOrdenFabricacionTerminada} e Instalacion usa
 * {@code tieneInstalacionCompletada}.</p>
 *
 * <p>Con {@link PerfilFasesGiro#ANUNCIOS} el resultado es identico a la derivacion
 * clasica (las cuatro fases en secuencia). Con {@link PerfilFasesGiro#GENERICO} solo
 * se evalua Produccion: todos los Sitios con Orden_Fabricacion terminada -&gt;
 * {@code COMPLETADO}; alguno sin ella -&gt; {@code EN_PRODUCCION}.</p>
 *
 * <p>Clase de utilidad no instanciable.</p>
 */
public final class DerivacionEstadoProyecto {

    private DerivacionEstadoProyecto() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Deriva el estado consolidado del Proyecto a partir del avance de sus Sitios
     * usando el perfil de fases de anuncios (las cuatro fases). Se conserva como
     * <strong>conveniencia</strong> para no romper llamadores/tests existentes;
     * delega en {@link #derivar(PerfilFasesGiro, List)} con
     * {@link PerfilFasesGiro#ANUNCIOS} (Req 3.5).
     *
     * @param avances lista con el avance de cada Sitio del Proyecto; nunca
     *                {@code null} (una lista vacia significa Proyecto sin Sitios).
     * @return el estado consolidado derivado con el perfil de anuncios.
     * @throws NullPointerException si {@code avances} es {@code null} o contiene
     *         algun elemento {@code null}.
     */
    public static EstadoConsolidadoProyecto derivar(List<AvanceFasesSitio> avances) {
        return derivar(PerfilFasesGiro.ANUNCIOS, avances);
    }

    /**
     * Deriva el estado consolidado del Proyecto a partir del perfil de fases
     * aplicables al giro y del avance de sus Sitios (Req 3.2, 3.3, 3.4). Funcion
     * pura: sin efectos secundarios y determinista (Req 3.6).
     *
     * @param perfil  perfil que indica que fases aplican al giro; nunca {@code null}.
     * @param avances lista con el avance de cada Sitio del Proyecto; nunca
     *                {@code null} (una lista vacia significa Proyecto sin Sitios).
     * @return el estado consolidado derivado.
     * @throws NullPointerException si {@code perfil} o {@code avances} son
     *         {@code null}, o si {@code avances} contiene algun elemento {@code null}.
     */
    public static EstadoConsolidadoProyecto derivar(
            PerfilFasesGiro perfil, List<AvanceFasesSitio> avances) {
        if (perfil == null) {
            throw new NullPointerException("El perfil de fases del giro es obligatorio.");
        }
        if (avances == null) {
            throw new NullPointerException("La lista de avances de los Sitios es obligatoria.");
        }
        if (avances.isEmpty()) {
            return EstadoConsolidadoProyecto.SIN_SITIOS;
        }

        boolean todosLevantamiento = true;
        boolean todosPermiso = true;
        boolean todosFabricacion = true;
        boolean todosInstalacion = true;

        for (AvanceFasesSitio avance : avances) {
            if (avance == null) {
                throw new NullPointerException(
                        "El avance de un Sitio no puede ser nulo.");
            }
            todosLevantamiento &= avance.tieneLevantamientoCompletado();
            todosPermiso &= avance.tienePermisoAprobado();
            todosFabricacion &= avance.tieneOrdenFabricacionTerminada();
            todosInstalacion &= avance.tieneInstalacionCompletada();
        }

        // Recorre las fases en orden operativo, evaluando solo las aplicables al
        // giro; devuelve la primera fase aplicable que no cubran todos los Sitios.
        if (perfil.aplica(FaseProyecto.LEVANTAMIENTO) && !todosLevantamiento) {
            return EstadoConsolidadoProyecto.EN_LEVANTAMIENTO;
        }
        if (perfil.aplica(FaseProyecto.PERMISO) && !todosPermiso) {
            return EstadoConsolidadoProyecto.EN_TRAMITE_PERMISOS;
        }
        if (perfil.aplica(FaseProyecto.PRODUCCION) && !todosFabricacion) {
            return EstadoConsolidadoProyecto.EN_PRODUCCION;
        }
        if (perfil.aplica(FaseProyecto.INSTALACION) && !todosInstalacion) {
            return EstadoConsolidadoProyecto.EN_INSTALACION;
        }
        return EstadoConsolidadoProyecto.COMPLETADO;
    }
}
