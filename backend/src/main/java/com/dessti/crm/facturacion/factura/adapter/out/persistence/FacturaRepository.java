package com.dessti.crm.facturacion.factura.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.facturacion.factura.domain.EstadoFactura;
import com.dessti.crm.facturacion.factura.domain.Factura;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Factura} (Req 34, 35,
 * 23). Replica el patron de {@code CotizacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Factura}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V30) lo refuerza. La
 * busqueda de una Factura de otro tenant devuelve vacio: la aplicacion lo traduce
 * a 404 y audita el intento (Req 23.3).</p>
 */
public interface FacturaRepository extends JpaRepository<Factura, UUID> {

    /**
     * Busca una Factura por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Factura.
     * @return la Factura, o vacio (que la aplicacion traduce a 404, Req 23.3).
     */
    Optional<Factura> findById(UUID id);

    /**
     * Listado paginado de Facturas del tenant vigente con filtros opcionales por
     * Cliente y por estado (Req 34.4). Cada filtro nulo se ignora.
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param estado    estado a filtrar; {@code null} no filtra por estado.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Facturas que cumplen los filtros.
     */
    @Query("""
            SELECT f FROM Factura f
            WHERE (:clienteId IS NULL OR f.clienteId = :clienteId)
              AND (:estado IS NULL OR f.estado = :estado)
            """)
    Page<Factura> buscarConFiltros(
            @Param("clienteId") UUID clienteId,
            @Param("estado") EstadoFactura estado,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> de la facturacion timbrada del
     * periodo (Req 22.1, 48.1): suma el {@code total} de las Facturas del tenant vigente
     * ya timbradas ({@code estado} distinto de {@code borrador} y no {@code cancelada}),
     * acotada por la fecha de timbrado. El filtro global de Hibernate y la RLS acotan la
     * consulta al {@code tenant_id} vigente (Req 23); no modifica dato alguno (Req 22.2).
     *
     * @param desde limite inferior de {@code fecha_timbrado} (inclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta limite superior de {@code fecha_timbrado} (exclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return el total facturado (timbrado y vigente) del periodo, o {@code 0}.
     */
    @Query("""
            SELECT COALESCE(SUM(f.total), 0) FROM Factura f
            WHERE f.estado = com.dessti.crm.facturacion.factura.domain.EstadoFactura.TIMBRADA
              AND f.fechaTimbrado >= :desde
              AND f.fechaTimbrado < :hasta
            """)
    BigDecimal sumarFacturacionTimbrada(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del IVA trasladado del periodo
     * (Req 22.1, 48.1): suma el {@code iva} de las Facturas timbradas vigentes del tenant
     * vigente, acotada por la fecha de timbrado. Acotada al {@code tenant_id} vigente por
     * el filtro de Hibernate y la RLS (Req 23).
     *
     * @param desde limite inferior de {@code fecha_timbrado} (inclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta limite superior de {@code fecha_timbrado} (exclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return el IVA trasladado del periodo, o {@code 0}.
     */
    // Cotas de fecha no nulas (sin ":param IS NULL OR ...") para que PostgreSQL infiera
    // el tipo del bind; la semantica "sin limite" se conserva con cotas centinela.
    @Query("""
            SELECT COALESCE(SUM(f.iva), 0) FROM Factura f
            WHERE f.estado = com.dessti.crm.facturacion.factura.domain.EstadoFactura.TIMBRADA
              AND f.fechaTimbrado >= :desde
              AND f.fechaTimbrado < :hasta
            """)
    BigDecimal sumarIvaTimbrado(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Facturas timbradas
     * vigentes del tenant vigente en el periodo (Req 22.1). Acotada al {@code tenant_id}
     * vigente por el filtro de Hibernate y la RLS (Req 23).
     *
     * @param desde limite inferior de {@code fecha_timbrado} (inclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta limite superior de {@code fecha_timbrado} (exclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return el conteo de Facturas timbradas del periodo.
     */
    // Cotas de fecha no nulas (sin ":param IS NULL OR ...") para que PostgreSQL infiera
    // el tipo del bind; la semantica "sin limite" se conserva con cotas centinela.
    @Query("""
            SELECT COUNT(f) FROM Factura f
            WHERE f.estado = com.dessti.crm.facturacion.factura.domain.EstadoFactura.TIMBRADA
              AND f.fechaTimbrado >= :desde
              AND f.fechaTimbrado < :hasta
            """)
    long contarTimbradas(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
