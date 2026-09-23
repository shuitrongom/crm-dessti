package com.dessti.crm.operacion.inventario.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.operacion.inventario.domain.Material;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Material} (Req 18, 23).
 * Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Material} extiende
 * {@code TenantScopedEntity}, el filtro global de Hibernate {@code tenantFilter} (Capa 1)
 * acota <em>automaticamente</em> estas consultas al {@code tenant_id} vigente, y la RLS
 * de PostgreSQL (Capa 2, V18) lo refuerza. La busqueda por {@code id} de un Material de
 * otro tenant devuelve vacio: la capa de aplicacion lo traduce a 404 y audita el intento
 * (Req 23.3).</p>
 */
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    /**
     * Busca un Material ACTIVO por su identificador dentro del tenant vigente. Un
     * Material inexistente, de otro tenant o dado de baja logica produce
     * {@link Optional#empty()} (que la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del Material.
     * @return el Material activo, o vacio.
     */
    Optional<Material> findByIdAndActivoTrue(UUID id);

    /**
     * Listado paginado de Materiales ACTIVOS del tenant vigente con filtros opcionales
     * por nombre (coincidencia parcial, insensible a mayusculas) y por condicion de
     * stock bajo (Req 18.6). Cada filtro nulo se ignora:
     *
     * <ul>
     *   <li>{@code nombre} nulo/blanco: no filtra por nombre.</li>
     *   <li>{@code soloStockBajo} {@code false} o {@code null}: no filtra por stock;
     *       {@code true}: solo Materiales con {@code existencias < stock_minimo}
     *       (condicion de stock bajo, Req 18.5).</li>
     * </ul>
     *
     * <p>Cuando ningun resultado coincide, la pagina resultante es vacia con
     * {@code totalElements = 0} (semantica de {@link Page}).</p>
     *
     * @param nombre        fragmento del nombre a filtrar; {@code null}/blanco no filtra.
     * @param soloStockBajo si {@code true}, restringe a Materiales en stock bajo.
     * @param pageable      parametros de paginacion ya acotados (20/100).
     * @return la pagina de Materiales activos que cumplen los filtros.
     */
    // El bind :nombre se envuelve en CAST(... AS string) para que Hibernate 6 emita
    // "cast(? as varchar)" y PostgreSQL infiera el tipo textual del parametro. Sin el
    // cast, cuando :nombre llega NULL (filtro ausente), el planificador de PostgreSQL
    // type-checkea LOWER(CONCAT('%', :nombre, '%')) antes del corto-circuito
    // ":nombre IS NULL", infiere el bind no tipado como bytea y falla con
    // "no existe la funcion lower(bytea)". El cast conserva la semantica: null/blanco no
    // filtra, coincidencia parcial sin distincion de mayusculas/minusculas si hay valor.
    @Query("""
            SELECT m FROM Material m
            WHERE m.activo = true
              AND (:nombre IS NULL
                   OR LOWER(m.nombre) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
              AND (:soloStockBajo = false OR m.existencias < m.stockMinimo)
            """)
    Page<Material> buscarConFiltros(
            @Param("nombre") String nombre,
            @Param("soloStockBajo") boolean soloStockBajo,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Materiales ACTIVOS del
     * tenant vigente en condicion de stock bajo, es decir {@code existencias <
     * stock_minimo} (Req 22.1, 18.5). El filtro global de Hibernate y la RLS acotan la
     * consulta al {@code tenant_id} vigente (Req 23); no modifica dato alguno (Req 22.2).
     *
     * @return el conteo de Materiales activos por debajo de su stock minimo.
     */
    @Query("""
            SELECT COUNT(m) FROM Material m
            WHERE m.activo = true AND m.existencias < m.stockMinimo
            """)
    long contarMaterialesBajoStockMinimo();

    /**
     * Agregacion de <strong>solo lectura</strong> del numero total de Materiales
     * ACTIVOS del tenant vigente (Req 22.1). Acotada al {@code tenant_id} vigente por el
     * filtro de Hibernate y la RLS (Req 23).
     *
     * @return el conteo de Materiales activos del tenant.
     */
    long countByActivoTrue();
}
