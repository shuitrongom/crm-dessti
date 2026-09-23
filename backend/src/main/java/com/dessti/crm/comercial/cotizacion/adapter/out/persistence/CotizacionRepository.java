package com.dessti.crm.comercial.cotizacion.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.comercial.cotizacion.domain.Cotizacion;
import com.dessti.crm.comercial.cotizacion.domain.EstadoCotizacion;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Cotizacion} (Req 6,
 * 23). Replica el patron de {@code OportunidadRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Cotizacion}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V14) lo refuerza. La
 * busqueda por {@code id} de una Cotizacion de otro tenant devuelve vacio: la capa
 * de aplicacion lo traduce a 404 y audita el intento (Req 4.3, 23.3).</p>
 */
public interface CotizacionRepository extends JpaRepository<Cotizacion, UUID> {

    /**
     * Busca una Cotizacion por su identificador dentro del tenant vigente. Una
     * Cotizacion inexistente o de otro tenant produce {@link Optional#empty()}
     * (que la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Cotizacion.
     * @return la Cotizacion, o vacio.
     */
    Optional<Cotizacion> findById(UUID id);

    /**
     * Listado paginado de Cotizaciones del tenant vigente con filtros opcionales
     * por Cliente y por estado (Req 6.8, 6.9). Cada filtro nulo se ignora
     * (coincide con cualquier valor), de modo que sin filtros se devuelven todas
     * las Cotizaciones del tenant.
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param estado    estado a filtrar; {@code null} no filtra por estado.
     * @param canalVentaId canal de venta a filtrar; {@code null} no filtra (Req 63.2).
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Cotizaciones que cumplen los filtros.
     */
    @Query("""
            SELECT c FROM Cotizacion c
            WHERE (:clienteId IS NULL OR c.clienteId = :clienteId)
              AND (:estado IS NULL OR c.estado = :estado)
              AND (:canalVentaId IS NULL OR c.canalVentaId = :canalVentaId)
            """)
    Page<Cotizacion> buscarConFiltros(
            @Param("clienteId") UUID clienteId,
            @Param("estado") EstadoCotizacion estado,
            @Param("canalVentaId") UUID canalVentaId,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> para el Tablero (Req 22.1) y el
     * analisis consolidado (Req 48.1): cuenta las Cotizaciones del tenant vigente
     * agrupadas por estado, acotadas de forma opcional por Cliente y por rango de
     * creacion. El filtro global de Hibernate y la RLS acotan la consulta al
     * {@code tenant_id} vigente (Req 23). No modifica dato alguno (Req 22.2).
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param desde     limite inferior de {@code created_at} (inclusivo); NO admite
     *                  {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}
     *                  para "sin limite inferior".
     * @param hasta     limite superior de {@code created_at} (exclusivo); NO admite
     *                  {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}
     *                  para "sin limite superior".
     * @return pares {@code [estado, conteo]} de las Cotizaciones del tenant.
     */
    // Cotas de fecha no nulas (sin ":param IS NULL OR ...") para que PostgreSQL infiera
    // el tipo del bind; la semantica "sin limite" se conserva con cotas centinela.
    @Query("""
            SELECT c.estado, COUNT(c) FROM Cotizacion c
            WHERE (:clienteId IS NULL OR c.clienteId = :clienteId)
              AND c.createdAt >= :desde
              AND c.createdAt < :hasta
            GROUP BY c.estado
            """)
    List<Object[]> contarPorEstado(
            @Param("clienteId") UUID clienteId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
