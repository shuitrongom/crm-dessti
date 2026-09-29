package com.dessti.crm.operacion.proyecto.application;

import java.util.UUID;

/**
 * Puerto de <strong>solo lectura</strong> del submodulo proyecto que expone las
 * precondiciones de negocio necesarias para <strong>avanzar manualmente</strong> la
 * fase operativa de un Sitio en el tablero (Req 3-bis). Lo consume
 * {@code ServicioProyectos.avanzarAvanceSitio} para bloquear un avance que aun no
 * cumple los requisitos del giro: no se puede pasar a {@code en_instalacion} sin un
 * Levantamiento_Sitio completado y un Permiso_Instalacion aprobado y vigente.
 *
 * <p>Publicarlo como puerto propio del Nucleo mantiene la arquitectura hexagonal: el
 * submodulo {@code proyecto} depende de esta <strong>interfaz estable</strong> y no
 * de la persistencia de los verticales. Tiene <strong>dos</strong> implementaciones
 * (patron D5-b, igual que {@link AvanceSitioPort}):
 * <ul>
 *   <li>{@code PrecondicionesFaseSitioAnunciosAdapter} (vertical anuncios): delega en
 *       {@code LevantamientoCompletadoPort} y {@code PermisoAprobadoPort} (permiso
 *       aprobado <strong>y vigente</strong>), acotados al tenant vigente (Req 23).</li>
 *   <li>{@code PrecondicionesFaseSitioGenericoAdapter} (Nucleo): devuelve {@code true}
 *       en ambos, porque los giros genericos no habilitan las fases de levantamiento
 *       ni permiso, y por tanto el gating de esas precondiciones no aplica.</li>
 * </ul>
 * {@code ServicioProyectos} selecciona el adaptador segun el {@code PerfilFasesGiro}
 * del tenant, evitando que un Proyecto generico dependa de los puertos de anuncios.</p>
 */
public interface PrecondicionesFaseSitioPort {

    /**
     * Indica si el Sitio dado tiene un Levantamiento_Sitio en estado {@code completado}
     * en el tenant vigente (Req 16.5). Precondicion para pasar a {@code en_preparacion}
     * y {@code en_instalacion} en el giro que habilita esa fase.
     *
     * @param sitioId identificador del Sitio a verificar; obligatorio.
     * @return {@code true} si el Sitio tiene un Levantamiento completado (o si el giro
     *         no habilita esa fase, en cuyo caso el gating no aplica).
     */
    boolean sitioTieneLevantamientoCompletado(UUID sitioId);

    /**
     * Indica si el Sitio dado tiene un Permiso_Instalacion aprobado <strong>y
     * vigente</strong> (no vencido a la fecha) en el tenant vigente (Req 17.4, 19.3).
     * Precondicion para pasar a {@code en_instalacion} en el giro que habilita esa fase:
     * un permiso aprobado pero vencido no autoriza instalar.
     *
     * @param sitioId identificador del Sitio a verificar; obligatorio.
     * @return {@code true} si el Sitio tiene un permiso aprobado y vigente (o si el giro
     *         no habilita esa fase, en cuyo caso el gating no aplica).
     */
    boolean sitioTienePermisoVigente(UUID sitioId);
}
