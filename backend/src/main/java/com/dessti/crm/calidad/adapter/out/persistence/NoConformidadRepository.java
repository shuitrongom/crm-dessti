package com.dessti.crm.calidad.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.calidad.domain.EstadoNoConformidad;
import com.dessti.crm.calidad.domain.NoConformidad;
import com.dessti.crm.calidad.domain.OrigenNoConformidad;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link NoConformidad} (Req 70.2,
 * 23). Provee el listado paginado con filtros (Req 12) y agregaciones de solo lectura
 * para los indicadores de calidad (Req 70.7).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de Hibernate
 * y la RLS de PostgreSQL (V47) acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface NoConformidadRepository extends JpaRepository<NoConformidad, UUID> {

    /**
     * Busca una No_Conformidad por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la No_Conformidad.
     * @return la No_Conformidad, o vacio (404 en la aplicacion, Req 23.3).
     */
    Optional<NoConformidad> findById(UUID id);

    /**
     * Listado paginado de No_Conformidades del tenant con filtros opcionales por origen
     * y estado (Req 70.2, 12). Cada filtro nulo se ignora.
     *
     * @param origen   origen a filtrar; {@code null} no filtra.
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados.
     * @return la pagina de No_Conformidades que cumplen los filtros.
     */
    @Query("""
            SELECT n FROM NoConformidad n
            WHERE (:origen IS NULL OR n.origen = :origen)
              AND (:estado IS NULL OR n.estado = :estado)
            """)
    Page<NoConformidad> buscar(
            @Param("origen") OrigenNoConformidad origen,
            @Param("estado") EstadoNoConformidad estado,
            Pageable pageable);

    /**
     * Cuenta las No_Conformidades del tenant en un estado dado (indicador de calidad,
     * Req 70.7).
     *
     * @param estado estado a contar.
     * @return el numero de No_Conformidades en ese estado.
     */
    long countByEstado(EstadoNoConformidad estado);
}
