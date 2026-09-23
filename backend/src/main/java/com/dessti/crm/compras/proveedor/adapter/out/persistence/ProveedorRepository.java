package com.dessti.crm.compras.proveedor.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.compras.proveedor.domain.Proveedor;

/**
 * Repositorio Spring Data JPA de la entidad {@link Proveedor} (Req 29, 23).
 * Replica el patron de {@code ClienteRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Proveedor}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> todas estas
 * consultas al {@code tenant_id} vigente, y la Row-Level Security de PostgreSQL
 * (Capa 2, V28) lo refuerza a nivel de base de datos. Por ello, la busqueda por
 * {@code id} de un Proveedor de <em>otro</em> tenant devuelve vacio: la capa de
 * aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface ProveedorRepository extends JpaRepository<Proveedor, UUID> {

    /**
     * Indica si ya existe un Proveedor <strong>activo</strong> con el RFC indicado
     * dentro del tenant vigente. Sustenta la comprobacion previa del conflicto de
     * unicidad de RFC por tenant entre activos (Req 29.2, 23.6) antes de confiar
     * en el indice unico parcial {@code uq_proveedor_rfc_activo_por_tenant} (V28).
     * El RFC debe compararse tal como se almacena (normalizado a mayusculas).
     *
     * @param rfc identificador fiscal normalizado (mayusculas).
     * @return {@code true} si existe un Proveedor activo con ese RFC en el tenant.
     */
    boolean existsByRfcAndActivoTrue(String rfc);

    /**
     * Busca un Proveedor <strong>activo</strong> por su identificador dentro del
     * tenant vigente. Un Proveedor inexistente, inactivo o de otro tenant produce
     * {@link Optional#empty()} (que la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del Proveedor.
     * @return el Proveedor activo, o vacio.
     */
    Optional<Proveedor> findByIdAndActivoTrue(UUID id);

    /**
     * Indica si existe un Proveedor <strong>activo</strong> con el identificador
     * indicado dentro del tenant vigente. La consume el submodulo de Ordenes de
     * Compra para verificar que la Orden se asocie a un Proveedor existente y
     * activo (Req 31.1) via {@code ProveedorExistentePort}.
     *
     * @param id identificador del Proveedor.
     * @return {@code true} si el Proveedor existe y esta activo en el tenant.
     */
    boolean existsByIdAndActivoTrue(UUID id);

    /**
     * Listado paginado de Proveedores <strong>activos</strong> del tenant vigente
     * cuyo nombre o RFC contiene el criterio indicado, sin distinguir mayusculas
     * de minusculas (Req 29.3, 29.6). Un {@code criterio} en blanco (cadena vacia)
     * coincide con todos los Proveedores activos, devolviendo el listado completo
     * paginado.
     *
     * @param criterio subcadena a buscar en nombre o RFC (se compara en minusculas).
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Proveedores activos que cumplen el filtro.
     */
    @Query("""
            SELECT p FROM Proveedor p
            WHERE p.activo = true
              AND (
                    LOWER(p.nombre) LIKE CONCAT('%', :criterio, '%')
                 OR LOWER(p.rfc)    LIKE CONCAT('%', :criterio, '%')
              )
            """)
    Page<Proveedor> buscarActivosPorNombreORfc(@Param("criterio") String criterio, Pageable pageable);
}
