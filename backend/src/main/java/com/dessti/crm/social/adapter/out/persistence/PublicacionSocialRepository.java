package com.dessti.crm.social.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.EstadoPublicacion;
import com.dessti.crm.social.domain.PublicacionSocial;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link PublicacionSocial}
 * (Req 65.1, 65.10, 23). Provee el listado paginado con filtros por Canal_Social y
 * estado. Sigue el patron de {@link CuentaCanalSocialRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de
 * Hibernate {@code tenantFilter} (Capa 1) y la RLS de PostgreSQL (Capa 2, V43)
 * acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface PublicacionSocialRepository extends JpaRepository<PublicacionSocial, UUID> {

    /**
     * Busca una Publicacion_Social por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Publicacion_Social.
     * @return la publicacion, o vacio (que la aplicacion traduce a 404, Req 23.3).
     */
    Optional<PublicacionSocial> findById(UUID id);

    /**
     * Listado paginado de Publicacion_Social del tenant con filtros opcionales por
     * Canal_Social y estado (Req 65.10). Cada filtro nulo se ignora. El orden lo
     * aporta el {@link Pageable}.
     *
     * @param canal    Canal_Social a filtrar; {@code null} no filtra.
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de publicaciones que cumplen los filtros.
     */
    @Query("""
            SELECT p FROM PublicacionSocial p
            WHERE (:canal IS NULL OR p.canal = :canal)
              AND (:estado IS NULL OR p.estado = :estado)
            """)
    Page<PublicacionSocial> buscarConFiltros(
            @Param("canal") CanalSocial canal,
            @Param("estado") EstadoPublicacion estado,
            Pageable pageable);
}
