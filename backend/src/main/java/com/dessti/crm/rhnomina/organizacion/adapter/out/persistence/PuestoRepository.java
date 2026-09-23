package com.dessti.crm.rhnomina.organizacion.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.rhnomina.organizacion.domain.Puesto;

/**
 * Repositorio Spring Data JPA de la entidad {@link Puesto} (Req 61, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Puesto}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, reforzado por la Row-Level Security (Capa 2, V36).
 * La busqueda por {@code id} de un Puesto de otro tenant devuelve vacio: la capa
 * de aplicacion lo traduce a 404 y audita el intento (Req 4.3, 23.3).</p>
 */
public interface PuestoRepository extends JpaRepository<Puesto, UUID> {

    /**
     * Busca un Puesto <strong>activo</strong> por su identificador dentro del
     * tenant vigente. Un Puesto inexistente, inactivo o de otro tenant produce
     * {@link Optional#empty()} (que la aplicacion traduce a 404, Req 4.3, 23.3).
     *
     * @param id identificador del Puesto.
     * @return el Puesto activo, o vacio.
     */
    Optional<Puesto> findByIdAndActivoTrue(UUID id);

    /**
     * Lista todos los Puestos <strong>activos</strong> del tenant vigente. Sustenta
     * la validacion aciclica de la jerarquia (Req 61.7) y la derivacion del
     * organigrama (Req 61.1): ambas necesitan el conjunto completo de Puestos y sus
     * superiores.
     *
     * @return la lista de Puestos activos del tenant.
     */
    List<Puesto> findByActivoTrue();

    /**
     * Listado paginado de Puestos del tenant vigente filtrable por nombre
     * (contiene, sin distinguir mayusculas) y por estado (Req 61.4). Un
     * {@code criterio} en blanco (cadena vacia) coincide con cualquier nombre; un
     * {@code activo} nulo no restringe por estado.
     *
     * @param criterio subcadena a buscar en el nombre (se compara en minusculas).
     * @param activo   estado a filtrar; {@code null} para no filtrar por estado.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Puestos que cumplen el filtro.
     */
    @Query("""
            SELECT p FROM Puesto p
            WHERE LOWER(p.nombre) LIKE CONCAT('%', :criterio, '%')
              AND (:activo IS NULL OR p.activo = :activo)
            """)
    Page<Puesto> buscarConFiltros(@Param("criterio") String criterio,
                                  @Param("activo") Boolean activo,
                                  Pageable pageable);
}
