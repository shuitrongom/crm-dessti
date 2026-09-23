package com.dessti.crm.compras.requisicion.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.compras.requisicion.domain.EstadoRequisicionCompra;
import com.dessti.crm.compras.requisicion.domain.RequisicionCompra;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link RequisicionCompra}
 * (Req 30, 23). Replica el patron de {@code CotizacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link RequisicionCompra} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas
 * consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V28) lo
 * refuerza. La busqueda por {@code id} de una requisicion de otro tenant devuelve
 * vacio: la capa de aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface RequisicionCompraRepository extends JpaRepository<RequisicionCompra, UUID> {

    /**
     * Busca una Requisicion_Compra por su identificador dentro del tenant vigente.
     * Una requisicion inexistente o de otro tenant produce {@link Optional#empty()}
     * (que la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Requisicion_Compra.
     * @return la Requisicion_Compra, o vacio.
     */
    Optional<RequisicionCompra> findById(UUID id);

    /**
     * Listado paginado de Requisiciones de Compra del tenant vigente con filtro
     * opcional por estado (Req 30.5). Un {@code estado} nulo se ignora (coincide
     * con cualquier valor), de modo que sin filtro se devuelven todas las
     * requisiciones del tenant.
     *
     * @param estado   estado a filtrar; {@code null} no filtra por estado.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Requisiciones de Compra que cumplen el filtro.
     */
    @Query("""
            SELECT r FROM RequisicionCompra r
            WHERE (:estado IS NULL OR r.estado = :estado)
            """)
    Page<RequisicionCompra> buscarConFiltros(
            @Param("estado") EstadoRequisicionCompra estado,
            Pageable pageable);
}
