package com.dessti.crm.estrategia.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.estrategia.domain.HistorialAvanceObjetivo;

/**
 * Repositorio Spring Data JPA del {@link HistorialAvanceObjetivo} (Req 58.4, 23),
 * historial <strong>append-only</strong> del avance de un objetivo: la aplicacion
 * solo inserta filas; nunca las modifica ni elimina.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> {@link HistorialAvanceObjetivo}
 * extiende {@code TenantScopedEntity}; el filtro global de Hibernate y la RLS de
 * V38 acotan automaticamente estas consultas al {@code tenant_id} vigente.</p>
 */
public interface HistorialAvanceObjetivoRepository
        extends JpaRepository<HistorialAvanceObjetivo, UUID> {
}
