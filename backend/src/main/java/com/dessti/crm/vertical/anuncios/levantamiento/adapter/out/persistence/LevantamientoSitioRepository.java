package com.dessti.crm.vertical.anuncios.levantamiento.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.vertical.anuncios.levantamiento.domain.EstadoLevantamiento;
import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoSitio;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link LevantamientoSitio}
 * (Req 16, 23). Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link LevantamientoSitio} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas
 * consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V19) lo
 * refuerza. La busqueda por {@code id} de un Levantamiento de otro tenant devuelve
 * vacio: la capa de aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface LevantamientoSitioRepository extends JpaRepository<LevantamientoSitio, UUID> {

    /**
     * Busca un Levantamiento_Sitio por su identificador dentro del tenant vigente.
     * Uno inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del Levantamiento_Sitio.
     * @return el Levantamiento_Sitio, o vacio.
     */
    Optional<LevantamientoSitio> findById(UUID id);

    /**
     * Indica si el Sitio dado tiene al menos un Levantamiento_Sitio en el estado
     * indicado dentro del tenant vigente. Con {@link EstadoLevantamiento#COMPLETADO}
     * implementa la guarda de programacion de instalacion (Req 16.5): la
     * instalacion de un Sitio solo puede programarse si esta consulta devuelve
     * {@code true}. La consume el bloque 22 via {@code LevantamientoCompletadoPort}.
     *
     * @param sitioId Sitio a verificar.
     * @param estado  estado a comprobar.
     * @return {@code true} si el Sitio tiene un Levantamiento en ese estado.
     */
    boolean existsBySitioIdAndEstado(UUID sitioId, EstadoLevantamiento estado);

    /**
     * Indica si un Levantamiento_Sitio concreto esta en el estado indicado dentro
     * del tenant vigente. Alternativa a {@link #existsBySitioIdAndEstado} cuando la
     * guarda se expresa por identificador de Levantamiento (Req 16.5).
     *
     * @param id     identificador del Levantamiento_Sitio.
     * @param estado estado a comprobar.
     * @return {@code true} si el Levantamiento existe (en el tenant) y esta en ese estado.
     */
    boolean existsByIdAndEstado(UUID id, EstadoLevantamiento estado);

    /**
     * Listado paginado de Levantamientos del tenant vigente con filtros opcionales
     * por estado y por Sitio (Req 16.6). Cada filtro nulo se ignora (coincide con
     * cualquier valor), de modo que sin filtros se devuelven todos los
     * Levantamientos del tenant. Cuando ningun resultado coincide, la pagina
     * resultante es vacia con {@code totalElements = 0}.
     *
     * @param estado  estado a filtrar; {@code null} no filtra por estado.
     * @param sitioId Sitio a filtrar; {@code null} no filtra por Sitio.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Levantamientos que cumplen los filtros.
     */
    @Query("""
            SELECT l FROM LevantamientoSitio l
            WHERE (:estado IS NULL OR l.estado = :estado)
              AND (:sitioId IS NULL OR l.sitioId = :sitioId)
            """)
    Page<LevantamientoSitio> buscarConFiltros(
            @Param("estado") EstadoLevantamiento estado,
            @Param("sitioId") UUID sitioId,
            Pageable pageable);
}
