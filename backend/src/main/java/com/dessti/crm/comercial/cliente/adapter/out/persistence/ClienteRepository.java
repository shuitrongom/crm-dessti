package com.dessti.crm.comercial.cliente.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.comercial.cliente.domain.Cliente;

/**
 * Repositorio Spring Data JPA de la entidad {@link Cliente} (Req 5, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Cliente}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> todas estas
 * consultas al {@code tenant_id} vigente, y la Row-Level Security de PostgreSQL
 * (Capa 2, V11) lo refuerza a nivel de base de datos. Por ello, la busqueda por
 * {@code id} de un Cliente de <em>otro</em> tenant devuelve vacio: la capa de
 * aplicacion lo traduce a 404 y audita el intento (Req 4.3, 23.3).</p>
 */
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    /**
     * Indica si ya existe un Cliente <strong>activo</strong> con el RFC indicado
     * dentro del tenant vigente. Sustenta la comprobacion previa del conflicto de
     * unicidad de RFC por tenant entre activos (Req 5.3, 23.6) antes de confiar
     * en el indice unico parcial {@code uq_cliente_rfc_activo_por_tenant} (V11).
     * El RFC debe compararse tal como se almacena (normalizado a mayusculas).
     *
     * @param rfc identificador fiscal normalizado (mayusculas).
     * @return {@code true} si existe un Cliente activo con ese RFC en el tenant.
     */
    boolean existsByRfcAndActivoTrue(String rfc);

    /**
     * Busca un Cliente <strong>activo</strong> por su identificador dentro del
     * tenant vigente. Un Cliente inexistente, inactivo o de otro tenant produce
     * {@link Optional#empty()} (que la aplicacion traduce a 404, Req 5.6, 23.3).
     *
     * @param id identificador del Cliente.
     * @return el Cliente activo, o vacio.
     */
    Optional<Cliente> findByIdAndActivoTrue(UUID id);

    /**
     * Listado paginado de Clientes <strong>activos</strong> del tenant vigente
     * cuyo nombre o RFC contiene el criterio indicado, sin distinguir mayusculas
     * de minusculas (Req 5.7, 5.8). Un {@code criterio} en blanco (cadena vacia)
     * coincide con todos los Clientes activos, devolviendo el listado completo
     * paginado.
     *
     * @param criterio subcadena a buscar en nombre o RFC (se compara en minusculas).
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Clientes activos que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM Cliente c
            WHERE c.activo = true
              AND (
                    LOWER(c.nombre) LIKE CONCAT('%', :criterio, '%')
                 OR LOWER(c.rfc)    LIKE CONCAT('%', :criterio, '%')
              )
            """)
    Page<Cliente> buscarActivosPorNombreORfc(@Param("criterio") String criterio, Pageable pageable);
}
