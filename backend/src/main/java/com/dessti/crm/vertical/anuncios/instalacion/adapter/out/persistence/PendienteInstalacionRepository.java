package com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.vertical.anuncios.instalacion.domain.PendienteInstalacion;

/**
 * Repositorio Spring Data JPA de las {@link PendienteInstalacion} de la
 * Lista_Pendientes de una Orden_Trabajo_Instalacion (Req 19.4, 19.6, 23). Replica
 * el patron tenant-scoped del modulo.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link PendienteInstalacion} extiende {@code TenantScopedEntity}, el filtro
 * global de Hibernate (Capa 1) y la RLS (Capa 2, V24) acotan estas consultas al
 * tenant vigente.</p>
 */
public interface PendienteInstalacionRepository extends JpaRepository<PendienteInstalacion, UUID> {

    /**
     * Devuelve las entradas de Lista_Pendientes de una Orden_Trabajo_Instalacion
     * del tenant vigente (Req 19.4), ordenadas por instante de alta ascendente.
     *
     * @param ordenTrabajoInstalacionId OTI cuyos pendientes se recuperan.
     * @return la lista de pendientes (posiblemente vacia).
     */
    List<PendienteInstalacion> findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(
            UUID ordenTrabajoInstalacionId);

    /**
     * Indica si la Orden_Trabajo_Instalacion tiene al menos un pendiente
     * <strong>sin resolver</strong> ({@code resuelto = false}) en el tenant vigente.
     * Es la guarda de cierre del Req 19.6: no se permite pasar la OTI a
     * {@code completada} mientras esta consulta devuelva {@code true}.
     *
     * @param ordenTrabajoInstalacionId OTI a verificar.
     * @return {@code true} si existe algun pendiente sin resolver en la OTI.
     */
    boolean existsByOrdenTrabajoInstalacionIdAndResueltoFalse(UUID ordenTrabajoInstalacionId);

    /**
     * Devuelve los pendientes <strong>sin resolver</strong> ({@code resuelto = false})
     * de una Orden_Trabajo_Instalacion del tenant vigente, ordenados por instante de
     * alta ascendente. Alimenta la guarda de cierre informativa (Req 8.5, diseno
     * &sect;C2): cuando la lista no esta vacia, el cierre a {@code completada} se
     * rechaza con 422 y el mensaje enumera las descripciones de estos pendientes.
     *
     * @param ordenTrabajoInstalacionId OTI cuyos pendientes sin resolver se recuperan.
     * @return la lista de pendientes sin resolver (posiblemente vacia).
     */
    List<PendienteInstalacion> findByOrdenTrabajoInstalacionIdAndResueltoFalseOrderByCreatedAtAsc(
            UUID ordenTrabajoInstalacionId);
}
