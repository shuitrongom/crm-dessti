package com.dessti.crm.notificaciones.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.notificaciones.domain.CanalNotificacion;
import com.dessti.crm.notificaciones.domain.EstadoNotificacion;
import com.dessti.crm.notificaciones.domain.Notificacion;
import com.dessti.crm.notificaciones.domain.TipoEventoNotificacion;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Notificacion} (Req 46,
 * 23). Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Notificacion}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V42) lo refuerza. La
 * busqueda por {@code id} de una Notificacion de otro tenant devuelve vacio: la capa
 * de aplicacion lo traduce a 404 (Req 23.3).</p>
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    /**
     * Busca una Notificacion por su identificador dentro del tenant vigente. Una
     * Notificacion inexistente o de otro tenant produce {@link Optional#empty()}
     * (que la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Notificacion.
     * @return la Notificacion, o vacio.
     */
    Optional<Notificacion> findById(UUID id);

    /**
     * Listado paginado de Notificaciones del tenant vigente con filtros opcionales
     * por estado, por evento de origen y por canal. Cada filtro nulo se ignora, de
     * modo que sin filtros se devuelven todas las Notificaciones del tenant. Sin
     * coincidencias, la pagina es vacia con {@code totalElements = 0}.
     *
     * @param estado   estado a filtrar; {@code null} no filtra por estado.
     * @param evento   evento de origen a filtrar; {@code null} no filtra por evento.
     * @param canal    canal a filtrar; {@code null} no filtra por canal.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Notificaciones que cumplen los filtros.
     */
    @Query("""
            SELECT n FROM Notificacion n
            WHERE (:estado IS NULL OR n.estado = :estado)
              AND (:evento IS NULL OR n.eventoOrigen = :evento)
              AND (:canal IS NULL OR n.canal = :canal)
            """)
    Page<Notificacion> buscarConFiltros(
            @Param("estado") EstadoNotificacion estado,
            @Param("evento") TipoEventoNotificacion evento,
            @Param("canal") CanalNotificacion canal,
            Pageable pageable);
}
