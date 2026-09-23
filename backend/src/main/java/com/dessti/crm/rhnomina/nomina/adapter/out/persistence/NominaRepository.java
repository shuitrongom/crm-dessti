package com.dessti.crm.rhnomina.nomina.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.rhnomina.nomina.domain.EstadoNomina;
import com.dessti.crm.rhnomina.nomina.domain.Nomina;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Nomina} (Req 41, 23).
 * Replica el patron de {@code FacturaRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Nomina} extiende
 * {@code TenantScopedEntity}, el filtro global de Hibernate {@code tenantFilter}
 * (Capa 1) acota <em>automaticamente</em> estas consultas al {@code tenant_id}
 * vigente, y la RLS de PostgreSQL (Capa 2, V34) lo refuerza. La busqueda de una
 * Nomina de otro tenant devuelve vacio: la aplicacion lo traduce a 404 y audita el
 * intento (Req 23.3).</p>
 */
public interface NominaRepository extends JpaRepository<Nomina, UUID> {

    /**
     * Busca una Nomina por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Nomina.
     * @return la Nomina, o vacio (que la aplicacion traduce a 404, Req 23.3).
     */
    Optional<Nomina> findById(UUID id);

    /**
     * Listado paginado de Nominas del tenant vigente con filtros opcionales por
     * Periodo_Nomina y por estado (Req 41). Cada filtro nulo/blanco se ignora.
     *
     * @param periodo codigo de Periodo_Nomina a filtrar; {@code null} no filtra.
     * @param estado  estado a filtrar; {@code null} no filtra por estado.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Nominas que cumplen los filtros.
     */
    @Query("""
            SELECT n FROM Nomina n
            WHERE (:periodo IS NULL OR n.periodoNomina = :periodo)
              AND (:estado IS NULL OR n.estado = :estado)
            """)
    Page<Nomina> buscarConFiltros(
            @Param("periodo") String periodo,
            @Param("estado") EstadoNomina estado,
            Pageable pageable);
}
