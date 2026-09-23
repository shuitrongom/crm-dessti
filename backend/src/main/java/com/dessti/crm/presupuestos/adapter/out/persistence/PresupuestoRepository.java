package com.dessti.crm.presupuestos.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.presupuestos.domain.Presupuesto;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Presupuesto} (Req 62,
 * 23). Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Presupuesto}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V40) lo refuerza. La
 * busqueda por {@code id} de un Presupuesto de otro tenant devuelve vacio: la capa de
 * aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface PresupuestoRepository extends JpaRepository<Presupuesto, UUID> {

    /**
     * Busca un Presupuesto por su identificador dentro del tenant vigente. Un
     * Presupuesto inexistente o de otro tenant produce {@link Optional#empty()} (que
     * la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del Presupuesto.
     * @return el Presupuesto, o vacio.
     */
    Optional<Presupuesto> findById(UUID id);

    /**
     * Indica si ya existe un Presupuesto para el {@code area} y {@code periodo} dados
     * en el tenant vigente. Es la pre-verificacion de la unicidad de negocio
     * "un Presupuesto por area y periodo" (Req 62.1). La unicidad
     * {@code (tenant_id, area, periodo)} de V40 lo refuerza a nivel de BD como segunda
     * capa de defensa ante concurrencia.
     *
     * @param area    area funcional a verificar.
     * @param periodo periodo a verificar.
     * @return {@code true} si ya existe un Presupuesto para ese area y periodo en el tenant.
     */
    boolean existsByAreaAndPeriodo(String area, String periodo);

    /**
     * Listado paginado de Presupuestos del tenant vigente con filtros opcionales por
     * area y por periodo (Req 62.4). Cada filtro nulo/blanco se ignora (coincide con
     * cualquier valor), de modo que sin filtros se devuelven todos los Presupuestos del
     * tenant. Cuando ningun resultado coincide, la pagina resultante es vacia con
     * {@code totalElements = 0} (semantica de {@link Page}).
     *
     * @param area     area a filtrar; {@code null} no filtra por area.
     * @param periodo  periodo a filtrar; {@code null} no filtra por periodo.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Presupuestos que cumplen los filtros.
     */
    @Query("""
            SELECT p FROM Presupuesto p
            WHERE (:area IS NULL OR p.area = :area)
              AND (:periodo IS NULL OR p.periodo = :periodo)
            """)
    Page<Presupuesto> buscarConFiltros(
            @Param("area") String area,
            @Param("periodo") String periodo,
            Pageable pageable);

    /**
     * Lista de <strong>solo lectura</strong> de todos los Presupuestos del tenant
     * vigente acotados de forma opcional por periodo, para agregar la variacion
     * presupuestal del Tablero (Req 22.1, 62.2). El filtro global de Hibernate y la RLS de
     * V40 acotan la consulta al {@code tenant_id} vigente (Req 23); no modifica dato
     * alguno (Req 22.2). El {@code periodo} se filtra por coincidencia exacta cuando se
     * indica.
     *
     * @param periodo periodo a filtrar; {@code null} no filtra por periodo.
     * @return los Presupuestos del tenant que cumplen el filtro.
     */
    @Query("""
            SELECT p FROM Presupuesto p
            WHERE (:periodo IS NULL OR p.periodo = :periodo)
            """)
    List<Presupuesto> buscarParaIndicadores(@Param("periodo") String periodo);
}
