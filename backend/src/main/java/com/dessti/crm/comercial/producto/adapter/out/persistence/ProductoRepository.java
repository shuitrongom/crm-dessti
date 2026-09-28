package com.dessti.crm.comercial.producto.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.comercial.producto.domain.Producto;

/**
 * Repositorio Spring Data JPA de la entidad {@link Producto} (Req 59, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Producto}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V12) lo refuerza. La
 * busqueda por {@code id} de un Producto de otro tenant devuelve vacio: la capa
 * de aplicacion lo traduce a 404 y audita el intento (Req 4.3, 23.3).</p>
 */
public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    /**
     * Busca un Producto <strong>activo</strong> por su identificador dentro del
     * tenant vigente. Un Producto inexistente, inactivo o de otro tenant produce
     * {@link Optional#empty()} (que la aplicacion traduce a 404, Req 23.3, 59.6).
     *
     * @param id identificador del Producto.
     * @return el Producto activo, o vacio.
     */
    Optional<Producto> findByIdAndActivoTrue(UUID id);

    /**
     * Listado paginado de Productos <strong>activos</strong> del tenant vigente
     * cuyo nombre contiene el criterio indicado, sin distinguir mayusculas
     * (Req 59.7). Un {@code criterio} en blanco (cadena vacia) coincide con todos
     * los Productos activos.
     *
     * @param criterio subcadena a buscar en el nombre (se compara en minusculas).
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Productos activos que cumplen el filtro.
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.activo = true
              AND LOWER(p.nombre) LIKE CONCAT('%', :criterio, '%')
            """)
    Page<Producto> buscarActivosPorNombre(@Param("criterio") String criterio, Pageable pageable);

    /**
     * Busca un Producto por su identificador dentro del tenant vigente
     * <strong>sin</strong> exigir que este activo. Sirve para la reactivacion de
     * un Producto dado de baja (Req 59.6): a diferencia de
     * {@link #findByIdAndActivoTrue(UUID)}, tambien resuelve Productos inactivos.
     * Un Producto de otro tenant sigue sin resolverse (aislamiento, Req 23.3).
     *
     * @param id identificador del Producto.
     * @return el Producto (activo o inactivo) del tenant, o vacio.
     */
    Optional<Producto> findById(UUID id);

    /**
     * Listado paginado de Productos del tenant vigente cuyo nombre contiene el
     * criterio (sin distinguir mayusculas), filtrable por estado activo/inactivo
     * o TODOS cuando {@code activo} es {@code null}. Extiende
     * {@link #buscarActivosPorNombre(String, Pageable)} para poder mostrar y
     * reactivar los Productos dados de baja (Req 59.6, 59.7).
     *
     * @param criterio subcadena a buscar en el nombre (en minusculas).
     * @param activo   {@code true}=activos, {@code false}=inactivos, {@code null}=todos.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Productos que cumplen el filtro.
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE LOWER(p.nombre) LIKE CONCAT('%', :criterio, '%')
              AND (:activo IS NULL OR p.activo = :activo)
            """)
    Page<Producto> buscarPorNombreYEstado(@Param("criterio") String criterio,
                                          @Param("activo") Boolean activo, Pageable pageable);
}
