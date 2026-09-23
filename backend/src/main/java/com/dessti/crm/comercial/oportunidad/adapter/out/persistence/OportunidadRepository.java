package com.dessti.crm.comercial.oportunidad.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.comercial.oportunidad.domain.EtapaOportunidad;
import com.dessti.crm.comercial.oportunidad.domain.Oportunidad;

/**
 * Repositorio Spring Data JPA de la entidad {@link Oportunidad} (Req 14, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Oportunidad}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V13) lo refuerza. La
 * busqueda por {@code id} de una Oportunidad de otro tenant devuelve vacio: la
 * capa de aplicacion lo traduce a 404 y audita el intento (Req 4.3, 23.3).</p>
 */
public interface OportunidadRepository extends JpaRepository<Oportunidad, UUID> {

    /**
     * Busca una Oportunidad por su identificador dentro del tenant vigente. Una
     * Oportunidad inexistente o de otro tenant produce {@link Optional#empty()}
     * (que la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Oportunidad.
     * @return la Oportunidad, o vacio.
     */
    Optional<Oportunidad> findById(UUID id);

    /**
     * Listado paginado de Oportunidades del tenant vigente con filtros opcionales
     * por Cliente, por etapa y por Usuario responsable (Req 14.7, 14.8). Cada
     * filtro nulo se ignora (coincide con cualquier valor), de modo que sin
     * filtros se devuelven todas las Oportunidades del tenant.
     *
     * @param clienteId    Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param etapa        etapa a filtrar; {@code null} no filtra por etapa.
     * @param responsableId Usuario responsable a filtrar; {@code null} no filtra.
     * @param canalVentaId canal de venta a filtrar; {@code null} no filtra (Req 63.2).
     * @param pageable     parametros de paginacion ya acotados (20/100).
     * @return la pagina de Oportunidades que cumplen los filtros.
     */
    @Query("""
            SELECT o FROM Oportunidad o
            WHERE (:clienteId IS NULL OR o.clienteId = :clienteId)
              AND (:etapa IS NULL OR o.etapa = :etapa)
              AND (:responsableId IS NULL OR o.responsableUsuarioId = :responsableId)
              AND (:canalVentaId IS NULL OR o.canalVentaId = :canalVentaId)
            """)
    Page<Oportunidad> buscarConFiltros(
            @Param("clienteId") UUID clienteId,
            @Param("etapa") EtapaOportunidad etapa,
            @Param("responsableId") UUID responsableId,
            @Param("canalVentaId") UUID canalVentaId,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> para el Tablero (Req 22.1) y el
     * analisis consolidado (Req 48.1): cuenta las Oportunidades del tenant vigente
     * agrupadas por etapa del pipeline, acotadas de forma opcional por Cliente y por
     * rango de creacion. El filtro global de Hibernate y la RLS acotan la consulta al
     * {@code tenant_id} vigente (Req 23). No modifica dato alguno (Req 22.2).
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param desde     limite inferior de {@code created_at} (inclusivo); NO admite
     *                  {@code null}: el adaptador pasa una cota centinela
     *                  ({@code RangoPeriodo.INSTANTE_MINIMO}) para "sin limite inferior".
     * @param hasta     limite superior de {@code created_at} (exclusivo); NO admite
     *                  {@code null}: el adaptador pasa una cota centinela
     *                  ({@code RangoPeriodo.INSTANTE_MAXIMO}) para "sin limite superior".
     * @return pares {@code [etapa, conteo]} de las Oportunidades del tenant.
     */
    // Las cotas de fecha se comparan directamente (sin patron ":param IS NULL OR ...")
    // porque el adaptador SIEMPRE aporta valores no nulos: asi PostgreSQL infiere el
    // tipo del bind y se evita "could not determine data type of parameter". La
    // semantica "sin limite" se conserva con las cotas centinela de RangoPeriodo.
    @Query("""
            SELECT o.etapa, COUNT(o) FROM Oportunidad o
            WHERE (:clienteId IS NULL OR o.clienteId = :clienteId)
              AND o.createdAt >= :desde
              AND o.createdAt < :hasta
            GROUP BY o.etapa
            """)
    List<Object[]> contarPorEtapa(
            @Param("clienteId") UUID clienteId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del valor estimado total del
     * pipeline abierto (Oportunidades cuya etapa no es final) del tenant vigente,
     * acotada de forma opcional por Cliente y por rango de creacion (Req 22.1, 48.1).
     * Las etapas finales {@code ganado}/{@code perdido} se excluyen del pipeline
     * abierto. El aislamiento por tenant lo garantizan el filtro de Hibernate y la RLS
     * (Req 23).
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param desde     limite inferior de {@code created_at} (inclusivo); NO admite
     *                  {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}
     *                  para "sin limite inferior".
     * @param hasta     limite superior de {@code created_at} (exclusivo); NO admite
     *                  {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}
     *                  para "sin limite superior".
     * @return el valor estimado total del pipeline abierto, o {@code null} si no hay
     *         Oportunidades abiertas.
     */
    // Cotas de fecha no nulas (ver contarPorEtapa): evita el fallo de inferencia de tipo
    // de PostgreSQL conservando la semantica "sin limite" via cotas centinela.
    @Query("""
            SELECT COALESCE(SUM(o.valorEstimado), 0) FROM Oportunidad o
            WHERE o.etapa NOT IN (com.dessti.crm.comercial.oportunidad.domain.EtapaOportunidad.GANADO,
                                  com.dessti.crm.comercial.oportunidad.domain.EtapaOportunidad.PERDIDO)
              AND (:clienteId IS NULL OR o.clienteId = :clienteId)
              AND o.createdAt >= :desde
              AND o.createdAt < :hasta
            """)
    BigDecimal sumarValorPipelineAbierto(
            @Param("clienteId") UUID clienteId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
