package com.dessti.crm.operacion.produccion.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link OrdenFabricacion}
 * (Req 7, 23). Replica el patron de {@code CotizacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link OrdenFabricacion} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas
 * consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V17) lo
 * refuerza. La busqueda por {@code id} de una Orden_Fabricacion de otro tenant
 * devuelve vacio: la capa de aplicacion lo traduce a 404 y audita el intento
 * (Req 23.3).</p>
 */
public interface OrdenFabricacionRepository extends JpaRepository<OrdenFabricacion, UUID> {

    /**
     * Busca una Orden_Fabricacion por su identificador dentro del tenant vigente.
     * Una OF inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Orden_Fabricacion.
     * @return la Orden_Fabricacion, o vacio.
     */
    Optional<OrdenFabricacion> findById(UUID id);

    /**
     * Indica si ya existe una Orden_Fabricacion vinculada a la Cotizacion dada en
     * el tenant vigente. Es la pre-verificacion de la precondicion "la Cotizacion
     * no tiene ya una OF" (Req 7.3, Property 7). La unicidad
     * {@code (tenant_id, cotizacion_id)} de V17 lo refuerza a nivel de BD como
     * segunda capa de defensa ante concurrencia.
     *
     * @param cotizacionId Cotizacion a verificar.
     * @return {@code true} si la Cotizacion ya tiene una OF asociada en el tenant.
     */
    boolean existsByCotizacionId(UUID cotizacionId);

    /**
     * Indica si la Orden_Fabricacion identificada esta en el estado indicado dentro
     * del tenant vigente. Con {@link EstadoOrdenFabricacion#TERMINADA} implementa la
     * guarda de creacion de la Orden_Trabajo_Instalacion (Req 19.2): una OTI solo
     * puede crearse a partir de una Orden_Fabricacion terminada. La consume el
     * bloque 22 (instalacion) via {@code OrdenFabricacionTerminadaPort}. La consulta
     * ya esta acotada al tenant vigente por el filtro global de Hibernate y la RLS
     * (Req 23), de modo que una OF de otro tenant no se considera accesible.
     *
     * @param id     identificador de la Orden_Fabricacion.
     * @param estado estado a comprobar.
     * @return {@code true} si la Orden_Fabricacion existe (en el tenant) y esta en
     *         ese estado.
     */
    boolean existsByIdAndEstado(UUID id, EstadoOrdenFabricacion estado);

    /**
     * Listado paginado de Ordenes de Fabricacion del tenant vigente con filtros
     * opcionales por estado y por Cliente (Req 7.9). Cada filtro nulo se ignora
     * (coincide con cualquier valor), de modo que sin filtros se devuelven todas
     * las OF del tenant. Cuando ningun resultado coincide, la pagina resultante es
     * vacia con {@code totalElements = 0} (semantica de {@link Page}, Req 7.9).
     *
     * @param estado    estado a filtrar; {@code null} no filtra por estado.
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Ordenes de Fabricacion que cumplen los filtros.
     */
    @Query("""
            SELECT o FROM OrdenFabricacion o
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:clienteId IS NULL OR o.clienteId = :clienteId)
            """)
    Page<OrdenFabricacion> buscarConFiltros(
            @Param("estado") EstadoOrdenFabricacion estado,
            @Param("clienteId") UUID clienteId,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> para el Tablero (Req 22.1) y el
     * analisis consolidado (Req 48.1): cuenta las Ordenes de Fabricacion del tenant
     * vigente agrupadas por estado, acotadas de forma opcional por rango de creacion.
     * El filtro global de Hibernate y la RLS acotan la consulta al {@code tenant_id}
     * vigente (Req 23). No modifica dato alguno (Req 22.2).
     *
     * @param desde limite inferior de {@code created_at} (inclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta limite superior de {@code created_at} (exclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return pares {@code [estado, conteo]} de las Ordenes de Fabricacion del tenant.
     */
    // Cotas de fecha no nulas (sin ":param IS NULL OR ...") para que PostgreSQL infiera
    // el tipo del bind; la semantica "sin limite" se conserva con cotas centinela.
    @Query("""
            SELECT o.estado, COUNT(o) FROM OrdenFabricacion o
            WHERE o.createdAt >= :desde
              AND o.createdAt < :hasta
            GROUP BY o.estado
            """)
    List<Object[]> contarPorEstado(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
