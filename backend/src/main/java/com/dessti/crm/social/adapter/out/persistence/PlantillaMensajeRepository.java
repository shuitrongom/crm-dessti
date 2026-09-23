package com.dessti.crm.social.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.PlantillaMensaje;

/**
 * Repositorio Spring Data JPA de la {@link PlantillaMensaje} (Req 64.7, 23). El
 * aislamiento multi-tenant lo aportan el filtro global de Hibernate y la RLS de
 * PostgreSQL (V41).
 */
public interface PlantillaMensajeRepository extends JpaRepository<PlantillaMensaje, UUID> {

    /**
     * Busca una plantilla por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Plantilla_Mensaje.
     * @return la plantilla, o vacio (404 en la aplicacion, Req 23.3).
     */
    Optional<PlantillaMensaje> findById(UUID id);

    /**
     * Busca una plantilla por canal y nombre dentro del tenant (unicidad de V41).
     *
     * @param canal  Canal_Social.
     * @param nombre nombre de la plantilla.
     * @return la plantilla, o vacio.
     */
    Optional<PlantillaMensaje> findByCanalAndNombre(CanalSocial canal, String nombre);

    /**
     * Listado paginado de plantillas del tenant con filtro opcional por canal
     * (Req 64.7).
     *
     * @param canal    Canal_Social a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de plantillas.
     */
    @Query("""
            SELECT p FROM PlantillaMensaje p
            WHERE (:canal IS NULL OR p.canal = :canal)
            """)
    Page<PlantillaMensaje> buscarConFiltros(@Param("canal") CanalSocial canal, Pageable pageable);
}
