package com.dessti.crm.activosfijos.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.activosfijos.domain.ActivoFijo;
import com.dessti.crm.activosfijos.domain.EstadoActivoFijo;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link ActivoFijo}
 * (Req 44, 23). Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link ActivoFijo}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V37) lo refuerza. La
 * busqueda por {@code id} de un Activo_Fijo de otro tenant devuelve vacio: la capa
 * de aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface ActivoFijoRepository extends JpaRepository<ActivoFijo, UUID> {

    /**
     * Busca un Activo_Fijo por su identificador dentro del tenant vigente. Un bien
     * inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del Activo_Fijo.
     * @return el Activo_Fijo, o vacio.
     */
    Optional<ActivoFijo> findById(UUID id);

    /**
     * Listado paginado de Activos_Fijos del tenant vigente con filtro opcional por
     * estado (Req 44.5). Un {@code estado} nulo se ignora (coincide con cualquier
     * valor), de modo que sin filtro se devuelven todos los bienes del tenant.
     * Cuando ningun resultado coincide, la pagina resultante es vacia con
     * {@code totalElements = 0}.
     *
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Activos_Fijos que cumplen el filtro.
     */
    @Query("""
            SELECT a FROM ActivoFijo a
            WHERE (:estado IS NULL OR a.estado = :estado)
            """)
    Page<ActivoFijo> buscarConFiltros(
            @Param("estado") EstadoActivoFijo estado,
            Pageable pageable);
}
