package com.dessti.crm.operacion.proyecto.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.operacion.proyecto.domain.AvanceSitio;

/**
 * Repositorio Spring Data JPA de la entidad {@link AvanceSitio} (V78, Req 3.2, 23):
 * el avance operativo generico y editable de un Sitio para Proyectos multi-sitio.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link AvanceSitio}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota automaticamente estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V78) lo refuerza.</p>
 */
public interface AvanceSitioRepository extends JpaRepository<AvanceSitio, UUID> {

    /**
     * Busca el avance de un Sitio dentro del tenant vigente (relacion 1:1 opcional).
     *
     * @param sitioId Sitio cuyo avance se solicita.
     * @return el avance del Sitio, o vacio si aun no se ha materializado.
     */
    Optional<AvanceSitio> findBySitioId(UUID sitioId);

    /**
     * Devuelve los avances de un conjunto de Sitios dentro del tenant vigente. Se usa
     * para consolidar el estado de un Proyecto cargando los avances de todos sus
     * Sitios en una sola consulta.
     *
     * @param sitioIds identificadores de los Sitios.
     * @return los avances existentes de esos Sitios (posiblemente vacio).
     */
    List<AvanceSitio> findBySitioIdIn(List<UUID> sitioIds);
}
