package com.dessti.crm.calidad.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.calidad.domain.EstadoOportunidadCalidad;
import com.dessti.crm.calidad.domain.OportunidadCalidad;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link OportunidadCalidad}
 * (Req 70.3, 23). Provee el listado paginado con filtros (Req 12).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de Hibernate
 * y la RLS de PostgreSQL (V47) acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface OportunidadCalidadRepository extends JpaRepository<OportunidadCalidad, UUID> {

    /**
     * Busca una Oportunidad_Calidad por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Oportunidad_Calidad.
     * @return la Oportunidad_Calidad, o vacio (404 en la aplicacion, Req 23.3).
     */
    Optional<OportunidadCalidad> findById(UUID id);

    /**
     * Listado paginado de Oportunidades de calidad del tenant con filtro opcional por
     * estado (Req 70.3, 12). El filtro nulo se ignora.
     *
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados.
     * @return la pagina de Oportunidades de calidad que cumplen el filtro.
     */
    @Query("""
            SELECT o FROM OportunidadCalidad o
            WHERE (:estado IS NULL OR o.estado = :estado)
            """)
    Page<OportunidadCalidad> buscar(
            @Param("estado") EstadoOportunidadCalidad estado,
            Pageable pageable);
}
