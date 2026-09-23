package com.dessti.crm.rhnomina.empleado.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.rhnomina.empleado.domain.Empleado;

/**
 * Repositorio Spring Data JPA de la entidad {@link Empleado} (Req 40, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Empleado}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> todas estas
 * consultas al {@code tenant_id} vigente, y la Row-Level Security de PostgreSQL
 * (Capa 2, V32) lo refuerza. Por ello, la busqueda por {@code id} de un Empleado
 * de <em>otro</em> tenant devuelve vacio: la capa de aplicacion lo traduce a 404
 * y audita el intento (Req 4.3, 23.3).</p>
 */
public interface EmpleadoRepository extends JpaRepository<Empleado, UUID> {

    /**
     * Indica si ya existe un Empleado <strong>activo</strong> con el RFC indicado
     * dentro del tenant vigente. Sustenta la comprobacion previa del conflicto de
     * unicidad de RFC por tenant entre activos antes de confiar en el indice unico
     * parcial {@code uq_empleado_rfc_activo_por_tenant} (V32). El RFC debe
     * compararse tal como se almacena (normalizado a mayusculas).
     *
     * @param rfc RFC normalizado (mayusculas).
     * @return {@code true} si existe un Empleado activo con ese RFC en el tenant.
     */
    boolean existsByRfcAndActivoTrue(String rfc);

    /**
     * Busca un Empleado <strong>activo</strong> por su identificador dentro del
     * tenant vigente. Un Empleado inexistente, inactivo o de otro tenant produce
     * {@link Optional#empty()} (que la aplicacion traduce a 404, Req 4.3, 23.3).
     *
     * @param id identificador del Empleado.
     * @return el Empleado activo, o vacio.
     */
    Optional<Empleado> findByIdAndActivoTrue(UUID id);

    /**
     * Lista todos los Empleados <strong>activos</strong> del tenant vigente,
     * ordenados por nombre. Sustenta el calculo de Nomina (Req 41.1): la nomina de
     * un Periodo_Nomina recorre los Empleados activos de la Empresa. El filtro
     * global de Hibernate y la RLS (V32) lo acotan al tenant vigente.
     *
     * @return la lista de Empleados activos del tenant, ordenada por nombre.
     */
    List<Empleado> findByActivoTrueOrderByNombreAsc();

    /**
     * Listado paginado de Empleados del tenant vigente filtrable por nombre
     * (contiene, sin distinguir mayusculas) y por estado (Req 40.5, 40.6). Un
     * {@code criterio} en blanco (cadena vacia) coincide con cualquier nombre; un
     * {@code activo} nulo no restringe por estado.
     *
     * @param criterio subcadena a buscar en el nombre (se compara en minusculas).
     * @param activo   estado a filtrar; {@code null} para no filtrar por estado.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Empleados que cumplen el filtro.
     */
    @Query("""
            SELECT e FROM Empleado e
            WHERE LOWER(e.nombre) LIKE CONCAT('%', :criterio, '%')
              AND (:activo IS NULL OR e.activo = :activo)
            """)
    Page<Empleado> buscarConFiltros(@Param("criterio") String criterio,
                                    @Param("activo") Boolean activo,
                                    Pageable pageable);
}
