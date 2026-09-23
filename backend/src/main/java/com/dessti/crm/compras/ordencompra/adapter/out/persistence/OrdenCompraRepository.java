package com.dessti.crm.compras.ordencompra.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.compras.ordencompra.domain.EstadoOrdenCompra;
import com.dessti.crm.compras.ordencompra.domain.OrdenCompra;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link OrdenCompra} (Req 31,
 * 23). Replica el patron de {@code CotizacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link OrdenCompra}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V28) lo refuerza. La
 * busqueda por {@code id} de una Orden_Compra de otro tenant devuelve vacio: la
 * capa de aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, UUID> {

    /**
     * Busca una Orden_Compra por su identificador dentro del tenant vigente. Una
     * Orden inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Orden_Compra.
     * @return la Orden_Compra, o vacio.
     */
    Optional<OrdenCompra> findById(UUID id);

    /**
     * Listado paginado de Ordenes de Compra del tenant vigente con filtros
     * opcionales por Proveedor y por estado (Req 31.7). Cada filtro nulo se ignora
     * (coincide con cualquier valor), de modo que sin filtros se devuelven todas
     * las Ordenes del tenant. Cuando ningun resultado coincide, la pagina es vacia
     * con {@code totalElements = 0}.
     *
     * @param proveedorId Proveedor a filtrar; {@code null} no filtra por Proveedor.
     * @param estado      estado a filtrar; {@code null} no filtra por estado.
     * @param pageable    parametros de paginacion ya acotados (20/100).
     * @return la pagina de Ordenes de Compra que cumplen los filtros.
     */
    @Query("""
            SELECT o FROM OrdenCompra o
            WHERE (:proveedorId IS NULL OR o.proveedorId = :proveedorId)
              AND (:estado IS NULL OR o.estado = :estado)
            """)
    Page<OrdenCompra> buscarConFiltros(
            @Param("proveedorId") UUID proveedorId,
            @Param("estado") EstadoOrdenCompra estado,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> para el Tablero (Req 22.1) y el
     * analisis consolidado (Req 48.1): cuenta las Ordenes de Compra del tenant vigente
     * agrupadas por estado, acotadas de forma opcional por rango de creacion. El filtro
     * global de Hibernate y la RLS acotan la consulta al {@code tenant_id} vigente
     * (Req 23). No modifica dato alguno (Req 22.2).
     *
     * @param desde limite inferior de {@code created_at} (inclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta limite superior de {@code created_at} (exclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return pares {@code [estado, conteo]} de las Ordenes de Compra del tenant.
     */
    // Cotas de fecha no nulas (sin ":param IS NULL OR ...") para que PostgreSQL infiera
    // el tipo del bind; la semantica "sin limite" se conserva con cotas centinela.
    @Query("""
            SELECT o.estado, COUNT(o) FROM OrdenCompra o
            WHERE o.createdAt >= :desde
              AND o.createdAt < :hasta
            GROUP BY o.estado
            """)
    List<Object[]> contarPorEstado(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
