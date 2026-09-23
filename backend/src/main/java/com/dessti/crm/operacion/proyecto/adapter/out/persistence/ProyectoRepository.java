package com.dessti.crm.operacion.proyecto.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.operacion.proyecto.domain.Proyecto;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Proyecto} (Req 21, 23).
 * Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Proyecto}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V25) lo refuerza. La
 * busqueda por {@code id} de un Proyecto de otro tenant devuelve vacio: la capa de
 * aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface ProyectoRepository extends JpaRepository<Proyecto, UUID> {

    /**
     * Busca un Proyecto por su identificador dentro del tenant vigente. Uno
     * inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del Proyecto.
     * @return el Proyecto, o vacio.
     */
    Optional<Proyecto> findById(UUID id);

    /**
     * Listado paginado de Proyectos del tenant vigente con filtro opcional por
     * Cliente (Req 21.5). Un {@code clienteId} nulo se ignora (coincide con
     * cualquier valor), de modo que sin filtro se devuelven todos los Proyectos del
     * tenant. Cuando ningun resultado coincide, la pagina resultante es vacia con
     * {@code totalElements = 0} (semantica de {@link Page}).
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Proyectos que cumplen el filtro.
     */
    @Query("""
            SELECT p FROM Proyecto p
            WHERE (:clienteId IS NULL OR p.clienteId = :clienteId)
            """)
    Page<Proyecto> buscarConFiltros(@Param("clienteId") UUID clienteId, Pageable pageable);
}
