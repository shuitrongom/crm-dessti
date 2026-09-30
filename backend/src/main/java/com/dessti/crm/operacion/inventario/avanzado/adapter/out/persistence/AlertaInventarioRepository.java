package com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.operacion.inventario.avanzado.domain.AlertaInventario;

/**
 * Repositorio Spring Data JPA de las alertas de stock {@link AlertaInventario} (Req 60, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> {@link AlertaInventario} extiende
 * {@code TenantScopedEntity}; el filtro global de Hibernate y la RLS de V88 acotan estas
 * consultas al {@code tenant_id} vigente.</p>
 */
public interface AlertaInventarioRepository extends JpaRepository<AlertaInventario, UUID> {

    /**
     * Lista de forma paginada las alertas del tenant vigente con filtros opcionales por
     * estado de seguimiento y por Almacen (Req 60), ordenadas por deteccion descendente
     * (las mas recientes primero). Cada filtro nulo se ignora.
     *
     * @param atendida  estado de seguimiento a filtrar; {@code null} no filtra.
     * @param almacenId Almacen a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de alertas que cumplen los filtros.
     */
    @Query("""
            SELECT a FROM AlertaInventario a
            WHERE (:atendida IS NULL OR a.atendida = :atendida)
              AND (CAST(:almacenId AS uuid) IS NULL OR a.almacenId = :almacenId)
            ORDER BY a.detectadaEn DESC
            """)
    Page<AlertaInventario> buscarConFiltros(
            @Param("atendida") Boolean atendida,
            @Param("almacenId") UUID almacenId,
            Pageable pageable);
}
