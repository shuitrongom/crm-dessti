package com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.operacion.inventario.avanzado.domain.Almacen;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Almacen} (Req 60, 23).
 * Replica el patron de {@code MaterialRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Almacen} extiende
 * {@code TenantScopedEntity}, el filtro global de Hibernate (Capa 1) acota
 * <em>automaticamente</em> estas consultas al {@code tenant_id} vigente, y la RLS de
 * PostgreSQL (Capa 2, V26) lo refuerza. La busqueda por {@code id} de un Almacen de otro
 * tenant devuelve vacio: la capa de aplicacion lo traduce a 404 y audita el intento
 * (Req 23.3).</p>
 */
public interface AlmacenRepository extends JpaRepository<Almacen, UUID> {

    /**
     * Listado paginado de Almacenes del tenant vigente con filtros opcionales por nombre
     * (coincidencia parcial, insensible a mayusculas) y por estado activo (Req 60). Cada
     * filtro nulo se ignora:
     *
     * <ul>
     *   <li>{@code nombre} nulo/blanco: no filtra por nombre.</li>
     *   <li>{@code activo} nulo: no filtra por estado; {@code true}/{@code false}
     *       restringe a Almacenes activos/inactivos.</li>
     * </ul>
     *
     * @param nombre   fragmento del nombre a filtrar; {@code null}/blanco no filtra.
     * @param activo   estado activo a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Almacenes que cumplen los filtros.
     */
    // El bind :nombre se envuelve en CAST(... AS string) para que Hibernate 6 emita
    // "cast(? as varchar)" y PostgreSQL infiera el tipo textual del parametro. Sin el
    // cast, cuando :nombre llega NULL (filtro ausente), el planificador de PostgreSQL
    // type-checkea LOWER(CONCAT('%', :nombre, '%')) antes del corto-circuito
    // ":nombre IS NULL", infiere el bind no tipado como bytea y falla con
    // "no existe la funcion lower(bytea)". El cast conserva la semantica: null/blanco no
    // filtra, coincidencia parcial sin distincion de mayusculas/minusculas si hay valor.
    @Query("""
            SELECT a FROM Almacen a
            WHERE (:nombre IS NULL
                   OR LOWER(a.nombre) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
              AND (:activo IS NULL OR a.activo = :activo)
            """)
    Page<Almacen> buscarConFiltros(
            @Param("nombre") String nombre,
            @Param("activo") Boolean activo,
            Pageable pageable);
}
