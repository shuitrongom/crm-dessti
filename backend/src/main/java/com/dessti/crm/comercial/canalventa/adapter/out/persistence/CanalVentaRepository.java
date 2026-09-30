package com.dessti.crm.comercial.canalventa.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.comercial.canalventa.domain.CanalVenta;

/**
 * Repositorio Spring Data JPA de la entidad {@link CanalVenta} (Req 63, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link CanalVenta}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V15) lo refuerza. La
 * busqueda por {@code id} de un canal de otro tenant devuelve vacio: la capa de
 * aplicacion lo traduce a 404 y audita el intento (Req 4.3, 23.3).</p>
 */
public interface CanalVentaRepository extends JpaRepository<CanalVenta, UUID> {

    /**
     * Busca un Canal_Venta <strong>activo</strong> por su identificador dentro
     * del tenant vigente. Un canal inexistente, inactivo o de otro tenant produce
     * {@link Optional#empty()} (que la aplicacion traduce a 404, Req 23.3, 63.1).
     * Sustenta ademas la verificacion de existencia al clasificar una Oportunidad
     * o una Cotizacion por canal (Req 63.1).
     *
     * @param id identificador del Canal_Venta.
     * @return el Canal_Venta activo, o vacio.
     */
    Optional<CanalVenta> findByIdAndActivoTrue(UUID id);

    /**
     * Indica si ya existe un Canal_Venta <strong>activo</strong> con el nombre
     * indicado (sin distinguir mayusculas) dentro del tenant vigente. Sustenta la
     * comprobacion previa del conflicto de unicidad de nombre por tenant entre
     * activos (Req 23.6) antes de confiar en el indice unico parcial
     * {@code uq_canal_venta_nombre_activo_por_tenant} (V15).
     *
     * @param nombreNormalizado nombre normalizado a minusculas.
     * @return {@code true} si existe un canal activo con ese nombre en el tenant.
     */
    @Query("""
            SELECT COUNT(c) > 0 FROM CanalVenta c
            WHERE c.activo = true
              AND LOWER(c.nombre) = :nombreNormalizado
            """)
    boolean existePorNombreActivo(@Param("nombreNormalizado") String nombreNormalizado);

    /**
     * Listado paginado de Canales de Venta <strong>activos</strong> del tenant
     * vigente cuyo nombre contiene el criterio indicado, sin distinguir
     * mayusculas (Req 63.1). Un {@code criterio} en blanco (cadena vacia) coincide
     * con todos los canales activos, devolviendo el listado completo paginado.
     *
     * @param criterio subcadena a buscar en el nombre (se compara en minusculas).
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de canales activos que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM CanalVenta c
            WHERE c.activo = true
              AND LOWER(c.nombre) LIKE CONCAT('%', :criterio, '%')
            """)
    Page<CanalVenta> buscarActivosPorNombre(@Param("criterio") String criterio, Pageable pageable);

    /**
     * Busca un Canal_Venta por su identificador dentro del tenant vigente
     * <strong>sin</strong> exigir que este activo. Sirve para la reactivacion de
     * un canal dado de baja (Req 63.1): a diferencia de
     * {@link #findByIdAndActivoTrue(UUID)}, tambien resuelve canales inactivos. Un
     * canal de otro tenant sigue sin resolverse (aislamiento, Req 23.3).
     *
     * @param id identificador del Canal_Venta.
     * @return el canal (activo o inactivo) del tenant, o vacio.
     */
    Optional<CanalVenta> findById(UUID id);

    /**
     * Listado paginado de Canales de Venta del tenant vigente cuyo nombre contiene
     * el criterio (sin distinguir mayusculas), filtrable por estado activo/inactivo
     * o TODOS cuando {@code activo} es {@code null}. Extiende
     * {@link #buscarActivosPorNombre(String, Pageable)} para poder mostrar y
     * reactivar los canales dados de baja (Req 63.1).
     *
     * @param criterio subcadena a buscar en el nombre (en minusculas).
     * @param activo   {@code true}=activos, {@code false}=inactivos, {@code null}=todos.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de canales que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM CanalVenta c
            WHERE LOWER(c.nombre) LIKE CONCAT('%', :criterio, '%')
              AND (:activo IS NULL OR c.activo = :activo)
            """)
    Page<CanalVenta> buscarPorNombreYEstado(@Param("criterio") String criterio,
                                            @Param("activo") Boolean activo, Pageable pageable);
}
