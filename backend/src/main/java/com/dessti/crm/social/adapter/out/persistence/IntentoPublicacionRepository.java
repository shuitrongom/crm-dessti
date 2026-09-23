package com.dessti.crm.social.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.social.domain.IntentoPublicacion;

/**
 * Repositorio Spring Data JPA de {@link IntentoPublicacion} (Req 65.6, 23): el
 * registro por intento de la politica de reintentos de la publicacion.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de
 * Hibernate {@code tenantFilter} (Capa 1) y la RLS de PostgreSQL (Capa 2, V43)
 * acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface IntentoPublicacionRepository extends JpaRepository<IntentoPublicacion, UUID> {

    /**
     * Devuelve el historial de intentos de una Publicacion_Social, ordenado por
     * numero de intento ascendente (Req 65.6).
     *
     * @param publicacionSocialId Publicacion_Social; obligatorio.
     * @return la lista de intentos ordenados.
     */
    List<IntentoPublicacion> findByPublicacionSocialIdOrderByNumeroIntentoAsc(UUID publicacionSocialId);
}
