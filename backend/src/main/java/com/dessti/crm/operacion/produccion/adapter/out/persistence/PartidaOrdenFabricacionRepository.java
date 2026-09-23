package com.dessti.crm.operacion.produccion.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.operacion.produccion.domain.PartidaOrdenFabricacion;

/**
 * Repositorio Spring Data JPA de las {@link PartidaOrdenFabricacion partidas} de una
 * Orden_Fabricacion (Req 5, §B1). Replica el patron de
 * {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 5.4, Req 23):</strong> como
 * {@link PartidaOrdenFabricacion} extiende {@code TenantScopedEntity}, el filtro
 * global de Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em>
 * estas consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2,
 * V66) lo refuerza. Las partidas de una OF de otro tenant no son visibles.</p>
 */
public interface PartidaOrdenFabricacionRepository
        extends JpaRepository<PartidaOrdenFabricacion, UUID> {

    /**
     * Devuelve las partidas de la Orden_Fabricacion indicada dentro del tenant
     * vigente (Req 5.5). Una OF sin partidas (o de otro tenant) produce una lista
     * vacia.
     *
     * @param ordenFabricacionId Orden_Fabricacion de la que se listan las partidas.
     * @return las partidas de la Orden_Fabricacion; lista vacia si no hay.
     */
    List<PartidaOrdenFabricacion> findByOrdenFabricacionId(UUID ordenFabricacionId);

    /**
     * Elimina todas las partidas de la Orden_Fabricacion indicada dentro del tenant
     * vigente. Se usa por la operacion de reemplazo de partidas (§B1): borra las
     * existentes antes de insertar el nuevo conjunto, dentro de la misma
     * transaccion.
     *
     * @param ordenFabricacionId Orden_Fabricacion cuyas partidas se eliminan.
     */
    void deleteByOrdenFabricacionId(UUID ordenFabricacionId);
}
