package com.dessti.crm.calidad.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.calidad.domain.EstadoRiesgo;
import com.dessti.crm.calidad.domain.NivelRiesgo;
import com.dessti.crm.calidad.domain.Riesgo;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link Riesgo} (Req 70.3, 23).
 * Provee el listado paginado con filtros (Req 12).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de Hibernate
 * y la RLS de PostgreSQL (V47) acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface RiesgoRepository extends JpaRepository<Riesgo, UUID> {

    /**
     * Busca un Riesgo por su identificador dentro del tenant vigente.
     *
     * @param id identificador del Riesgo.
     * @return el Riesgo, o vacio (404 en la aplicacion, Req 23.3).
     */
    Optional<Riesgo> findById(UUID id);

    /**
     * Listado paginado de Riesgos del tenant con filtros opcionales por estado y nivel
     * derivado (Req 70.3, 12). Cada filtro nulo se ignora.
     *
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param nivel    nivel derivado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados.
     * @return la pagina de Riesgos que cumplen los filtros.
     */
    @Query("""
            SELECT r FROM Riesgo r
            WHERE (:estado IS NULL OR r.estado = :estado)
              AND (:nivel IS NULL OR r.nivelDerivado = :nivel)
            """)
    Page<Riesgo> buscar(
            @Param("estado") EstadoRiesgo estado,
            @Param("nivel") NivelRiesgo nivel,
            Pageable pageable);
}
