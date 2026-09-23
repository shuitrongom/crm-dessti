package com.dessti.crm.comercial.cotizacion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion;

/**
 * Repositorio Spring Data JPA de la entidad hija {@link PartidaCotizacion}
 * (Req 6.3, 23).
 *
 * <p>Las partidas son hijos del agregado {@link com.dessti.crm.comercial.cotizacion.domain.Cotizacion},
 * que las persiste y elimina en cascada; por ello no se requieren operaciones de
 * consulta propias en el flujo actual. Este repositorio se declara para completar
 * el patron de persistencia del submodulo (y facilitar consultas puntuales futuras).</p>
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link PartidaCotizacion} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate (Capa 1) y la RLS de PostgreSQL (Capa 2, V14) acotan
 * automaticamente toda operacion al {@code tenant_id} vigente.</p>
 */
public interface PartidaCotizacionRepository extends JpaRepository<PartidaCotizacion, UUID> {
}
