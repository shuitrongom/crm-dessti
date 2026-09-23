package com.dessti.crm.reportesbi.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.reportesbi.domain.TableroPersonalizado;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link TableroPersonalizado}
 * (Req 48.3, 23). Replica el patron de los repositorios tenant-scoped del sistema.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23, 48.5):</strong> como
 * {@link TableroPersonalizado} extiende {@code TenantScopedEntity}, el filtro global de
 * Hibernate {@code tenantFilter} acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (V44) lo refuerza. La busqueda por
 * {@code id} de un tablero de otro tenant devuelve vacio: la capa de aplicacion lo
 * traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface TableroPersonalizadoRepository extends JpaRepository<TableroPersonalizado, UUID> {

    /**
     * Busca un tablero personalizado por su identificador dentro del tenant vigente,
     * cargando sus widgets en la misma consulta para evitar N+1 al proyectar el DTO.
     * Un tablero inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del tablero.
     * @return el tablero con sus widgets, o vacio.
     */
    @EntityGraph(attributePaths = "widgets")
    Optional<TableroPersonalizado> findById(UUID id);

    /**
     * Listado paginado de los tableros personalizados del tenant vigente, ordenables
     * por los parametros de paginacion (Req 48.3). Sin resultados devuelve una pagina
     * vacia con total 0.
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de tableros del tenant.
     */
    Page<TableroPersonalizado> findAll(Pageable pageable);
}
