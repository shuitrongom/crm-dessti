package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.vertical.anuncios.mantenimiento.domain.ContratoMantenimiento;

/**
 * Repositorio Spring Data JPA de la raiz del agregado
 * {@link ContratoMantenimiento} (Req 20.1, 23). Replica el patron de
 * {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link ContratoMantenimiento} extiende {@code TenantScopedEntity}, el filtro
 * global de Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em>
 * estas consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2,
 * V27) lo refuerza. La busqueda por {@code id} de un contrato de otro tenant
 * devuelve vacio: la capa de aplicacion lo traduce a 404 y audita el intento
 * (Req 23.3).</p>
 */
public interface ContratoMantenimientoRepository
        extends JpaRepository<ContratoMantenimiento, UUID> {

    /**
     * Busca un Contrato_Mantenimiento por su identificador dentro del tenant
     * vigente. Un contrato inexistente o de otro tenant produce
     * {@link Optional#empty()} (que la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del Contrato_Mantenimiento.
     * @return el Contrato_Mantenimiento, o vacio.
     */
    Optional<ContratoMantenimiento> findById(UUID id);

    /**
     * Listado paginado de Contratos_Mantenimiento del tenant vigente con filtro
     * opcional por Cliente (Req 20.1, 20.7). El filtro nulo se ignora (coincide con
     * cualquier valor). Cuando ningun resultado coincide, la pagina resultante es
     * vacia con {@code totalElements = 0} (semantica de {@link Page}).
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Contratos_Mantenimiento que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM ContratoMantenimiento c
            WHERE (:clienteId IS NULL OR c.clienteId = :clienteId)
            """)
    Page<ContratoMantenimiento> buscarConFiltros(
            @Param("clienteId") UUID clienteId,
            Pageable pageable);
}
