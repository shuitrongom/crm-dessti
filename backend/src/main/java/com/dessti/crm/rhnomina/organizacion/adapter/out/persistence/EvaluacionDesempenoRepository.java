package com.dessti.crm.rhnomina.organizacion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.rhnomina.organizacion.domain.EvaluacionDesempeno;

/**
 * Repositorio Spring Data JPA de la entidad {@link EvaluacionDesempeno}
 * (Req 61.3, 61.4, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link EvaluacionDesempeno} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate {@code tenantFilter} (Capa 1) acota automaticamente estas consultas
 * al {@code tenant_id} vigente, reforzado por la Row-Level Security (Capa 2, V36).</p>
 */
public interface EvaluacionDesempenoRepository extends JpaRepository<EvaluacionDesempeno, UUID> {

    /**
     * Listado paginado de Evaluacion_Desempeno del tenant vigente filtrable por
     * Empleado y por periodo (Req 61.4). El HISTORIAL se conserva: se devuelven
     * todas las evaluaciones (varias por Empleado/periodo). Un {@code empleadoId}
     * nulo no restringe por Empleado; un {@code periodo} nulo o en blanco no
     * restringe por periodo.
     *
     * @param empleadoId Empleado a filtrar; {@code null} para no filtrar.
     * @param periodo    periodo a filtrar; {@code null}/blanco para no filtrar.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de Evaluacion_Desempeno que cumplen el filtro.
     */
    @Query("""
            SELECT e FROM EvaluacionDesempeno e
            WHERE (:empleadoId IS NULL OR e.empleadoId = :empleadoId)
              AND (:periodo IS NULL OR e.periodo = :periodo)
            """)
    Page<EvaluacionDesempeno> buscarConFiltros(@Param("empleadoId") UUID empleadoId,
                                               @Param("periodo") String periodo,
                                               Pageable pageable);
}
