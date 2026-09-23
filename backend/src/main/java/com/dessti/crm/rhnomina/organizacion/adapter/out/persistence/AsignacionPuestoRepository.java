package com.dessti.crm.rhnomina.organizacion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.rhnomina.organizacion.domain.AsignacionPuesto;

/**
 * Repositorio Spring Data JPA de la entidad {@link AsignacionPuesto} (Req 61.2, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link AsignacionPuesto} extiende {@code TenantScopedEntity}, el filtro global de
 * Hibernate {@code tenantFilter} (Capa 1) acota automaticamente estas consultas al
 * {@code tenant_id} vigente, reforzado por la Row-Level Security (Capa 2, V36).</p>
 */
public interface AsignacionPuestoRepository extends JpaRepository<AsignacionPuesto, UUID> {
}
