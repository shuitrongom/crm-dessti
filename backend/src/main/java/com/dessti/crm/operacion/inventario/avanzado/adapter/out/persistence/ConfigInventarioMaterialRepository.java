package com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.operacion.inventario.avanzado.domain.ConfigInventarioMaterial;

/**
 * Repositorio Spring Data JPA de la configuracion de inventario por Material
 * {@link ConfigInventarioMaterial} (Req 60, 23), en relacion 1:1 con {@code material}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> {@link ConfigInventarioMaterial}
 * extiende {@code TenantScopedEntity}; el filtro global de Hibernate y la RLS de V26
 * acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface ConfigInventarioMaterialRepository
        extends JpaRepository<ConfigInventarioMaterial, UUID> {

    /**
     * Busca la configuracion de inventario de un Material dentro del tenant vigente
     * (relacion 1:1, Req 60). Vacio si el Material aun no tiene configuracion avanzada.
     *
     * @param materialId identificador del Material.
     * @return la configuracion del Material, o vacio.
     */
    Optional<ConfigInventarioMaterial> findByMaterialId(UUID materialId);
}
