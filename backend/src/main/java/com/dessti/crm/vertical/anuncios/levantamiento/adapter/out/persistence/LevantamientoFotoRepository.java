package com.dessti.crm.vertical.anuncios.levantamiento.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoFoto;

/**
 * Repositorio Spring Data JPA de las {@link LevantamientoFoto} adjuntas a un
 * Levantamiento_Sitio (Req 16.3, 23). Replica el patron tenant-scoped del modulo.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link LevantamientoFoto} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate (Capa 1) y la RLS (Capa 2, V19) acotan estas consultas al tenant
 * vigente.</p>
 */
public interface LevantamientoFotoRepository extends JpaRepository<LevantamientoFoto, UUID> {

    /**
     * Devuelve las fotografias adjuntas a un Levantamiento_Sitio del tenant
     * vigente (Req 16.3), ordenadas por instante de alta ascendente.
     *
     * @param levantamientoId Levantamiento cuyas fotos se recuperan.
     * @return la lista de fotografias (posiblemente vacia).
     */
    List<LevantamientoFoto> findByLevantamientoIdOrderByCreatedAtAsc(UUID levantamientoId);
}
