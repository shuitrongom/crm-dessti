package com.dessti.crm.social.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.social.domain.CampanaPublicitaria;
import com.dessti.crm.social.domain.CanalSocial;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link CampanaPublicitaria}
 * (Req 65.7, 65.10, 23). Provee el listado paginado con filtro por Canal_Social.
 * Sigue el patron de {@link CuentaCanalSocialRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de
 * Hibernate {@code tenantFilter} (Capa 1) y la RLS de PostgreSQL (Capa 2, V43)
 * acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface CampanaPublicitariaRepository extends JpaRepository<CampanaPublicitaria, UUID> {

    /**
     * Busca una Campaña_Publicitaria por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Campaña_Publicitaria.
     * @return la campaña, o vacio (que la aplicacion traduce a 404, Req 23.3).
     */
    Optional<CampanaPublicitaria> findById(UUID id);

    /**
     * Listado paginado de Campaña_Publicitaria del tenant con filtro opcional por
     * Canal_Social (Req 65.10). El filtro nulo se ignora. El orden lo aporta el
     * {@link Pageable}.
     *
     * @param canal    Canal_Social a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de campañas que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM CampanaPublicitaria c
            WHERE (:canal IS NULL OR c.canal = :canal)
            """)
    Page<CampanaPublicitaria> buscarConFiltros(@Param("canal") CanalSocial canal, Pageable pageable);
}
