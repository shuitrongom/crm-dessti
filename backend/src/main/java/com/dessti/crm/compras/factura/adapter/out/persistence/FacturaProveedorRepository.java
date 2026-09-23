package com.dessti.crm.compras.factura.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.compras.factura.domain.EstadoFacturaProveedor;
import com.dessti.crm.compras.factura.domain.FacturaProveedor;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link FacturaProveedor}
 * (Req 33, 23). Replica el patron de {@code OrdenCompraRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link FacturaProveedor} extiende {@code TenantScopedEntity}, el filtro global de
 * Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas
 * consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V29) lo
 * refuerza. La busqueda por {@code id} de una factura de otro tenant devuelve
 * vacio: la capa de aplicacion lo traduce a 404 (Req 23.3).</p>
 */
public interface FacturaProveedorRepository extends JpaRepository<FacturaProveedor, UUID> {

    /**
     * Busca una Factura_Proveedor por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la factura.
     * @return la factura, o vacio (que la aplicacion traduce a 404, Req 23.3).
     */
    Optional<FacturaProveedor> findById(UUID id);

    /**
     * Listado paginado de Facturas de Proveedor del tenant vigente con filtros
     * opcionales por Proveedor, por Orden_Compra y por estado (Req 33.8). Cada
     * filtro nulo se ignora.
     *
     * @param proveedorId   Proveedor a filtrar; {@code null} no filtra.
     * @param ordenCompraId Orden_Compra a filtrar; {@code null} no filtra.
     * @param estado        estado a filtrar; {@code null} no filtra.
     * @param pageable      parametros de paginacion ya acotados (20/100).
     * @return la pagina de facturas que cumplen los filtros.
     */
    @Query("""
            SELECT f FROM FacturaProveedor f
            WHERE (:proveedorId IS NULL OR f.proveedorId = :proveedorId)
              AND (:ordenCompraId IS NULL OR f.ordenCompraId = :ordenCompraId)
              AND (:estado IS NULL OR f.estado = :estado)
            """)
    Page<FacturaProveedor> buscarConFiltros(
            @Param("proveedorId") UUID proveedorId,
            @Param("ordenCompraId") UUID ordenCompraId,
            @Param("estado") EstadoFacturaProveedor estado,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Facturas de Proveedor
     * del tenant vigente en el estado indicado, acotada de forma opcional por rango de
     * creacion (Req 22.1). Con {@link EstadoFacturaProveedor#DISCREPANCIA} produce el
     * conteo de facturas con discrepancia de la conciliacion de tres vias (Req 33.4). El
     * filtro global de Hibernate y la RLS acotan la consulta al {@code tenant_id} vigente
     * (Req 23); no modifica dato alguno (Req 22.2).
     *
     * @param estado estado a contar; obligatorio.
     * @param desde  limite inferior de {@code created_at} (inclusivo); NO admite
     *               {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta  limite superior de {@code created_at} (exclusivo); NO admite
     *               {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return el conteo de facturas del tenant en ese estado.
     */
    // Cotas de fecha no nulas (sin ":param IS NULL OR ...") para que PostgreSQL infiera
    // el tipo del bind; la semantica "sin limite" se conserva con cotas centinela.
    @Query("""
            SELECT COUNT(f) FROM FacturaProveedor f
            WHERE f.estado = :estado
              AND f.createdAt >= :desde
              AND f.createdAt < :hasta
            """)
    long contarPorEstado(
            @Param("estado") EstadoFacturaProveedor estado,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
