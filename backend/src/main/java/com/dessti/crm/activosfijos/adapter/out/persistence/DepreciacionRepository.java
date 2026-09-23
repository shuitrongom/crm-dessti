package com.dessti.crm.activosfijos.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.activosfijos.domain.Depreciacion;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Depreciacion}
 * (Req 44.3, 23). Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Depreciacion}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V37) lo refuerza.</p>
 */
public interface DepreciacionRepository extends JpaRepository<Depreciacion, UUID> {

    /**
     * Indica si ya existe una Depreciacion registrada para el Activo_Fijo y el
     * periodo indicados dentro del tenant vigente. Es la pre-verificacion de "una
     * depreciacion por Activo_Fijo y periodo" (Req 44.3); el UNIQUE
     * {@code (tenant_id, activo_fijo_id, periodo)} de V37 lo refuerza a nivel de BD
     * como segunda capa de defensa ante concurrencia.
     *
     * @param activoFijoId Activo_Fijo a verificar.
     * @param periodo      periodo mensual {@code 'AAAA-MM'} a verificar.
     * @return {@code true} si ya existe la depreciacion de ese activo y periodo.
     */
    boolean existsByActivoFijoIdAndPeriodo(UUID activoFijoId, String periodo);
}
