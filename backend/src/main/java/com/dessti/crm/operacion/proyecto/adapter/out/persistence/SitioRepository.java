package com.dessti.crm.operacion.proyecto.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.operacion.proyecto.domain.Sitio;

/**
 * Repositorio Spring Data JPA de la entidad {@link Sitio} (Req 21.2, 23), tabla
 * hija del agregado Proyecto.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Sitio}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V25) lo refuerza.</p>
 */
public interface SitioRepository extends JpaRepository<Sitio, UUID> {

    /**
     * Devuelve los Sitios de un Proyecto dentro del tenant vigente, ordenados por
     * fecha de alta ascendente. Da soporte a la consulta detallada del Proyecto
     * (Req 21.4), en la que se computa el avance de cada Sitio. Si el Proyecto no
     * tiene Sitios, devuelve una lista vacia.
     *
     * @param proyectoId Proyecto cuyos Sitios se solicitan.
     * @return los Sitios del Proyecto (posiblemente vacio).
     */
    List<Sitio> findByProyectoIdOrderByCreatedAtAsc(UUID proyectoId);
}
