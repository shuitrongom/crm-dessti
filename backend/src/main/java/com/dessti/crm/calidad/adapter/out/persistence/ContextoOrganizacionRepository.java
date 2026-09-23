package com.dessti.crm.calidad.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.calidad.domain.ContextoOrganizacion;
import com.dessti.crm.calidad.domain.TipoContexto;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link ContextoOrganizacion}
 * (Req 70.5, 23). Provee el listado paginado con filtros (Req 12).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de Hibernate
 * y la RLS de PostgreSQL (V47) acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface ContextoOrganizacionRepository extends JpaRepository<ContextoOrganizacion, UUID> {

    /**
     * Busca un Contexto_Organizacion por su identificador dentro del tenant vigente.
     *
     * @param id identificador del Contexto_Organizacion.
     * @return el Contexto_Organizacion, o vacio (404 en la aplicacion, Req 23.3).
     */
    Optional<ContextoOrganizacion> findById(UUID id);

    /**
     * Listado paginado de cuestiones de contexto del tenant con filtros opcionales por
     * tipo y pertinencia del cambio climatico (Req 70.5, 12). Cada filtro nulo se ignora.
     *
     * @param tipo            tipo a filtrar; {@code null} no filtra.
     * @param climaPertinente pertinencia del clima a filtrar; {@code null} no filtra.
     * @param pageable        parametros de paginacion ya acotados.
     * @return la pagina de cuestiones de contexto que cumplen los filtros.
     */
    @Query("""
            SELECT c FROM ContextoOrganizacion c
            WHERE (:tipo IS NULL OR c.tipo = :tipo)
              AND (:climaPertinente IS NULL OR c.climaPertinente = :climaPertinente)
            """)
    Page<ContextoOrganizacion> buscar(
            @Param("tipo") TipoContexto tipo,
            @Param("climaPertinente") Boolean climaPertinente,
            Pageable pageable);
}
