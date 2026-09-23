package com.dessti.crm.calidad.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.calidad.domain.CambioSgc;
import com.dessti.crm.calidad.domain.EstadoCambioSgc;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link CambioSgc} (Req 70.4, 23).
 * Provee el listado paginado con filtros (Req 12).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de Hibernate
 * y la RLS de PostgreSQL (V47) acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface CambioSgcRepository extends JpaRepository<CambioSgc, UUID> {

    /**
     * Busca un Cambio_SGC por su identificador dentro del tenant vigente.
     *
     * @param id identificador del Cambio_SGC.
     * @return el Cambio_SGC, o vacio (404 en la aplicacion, Req 23.3).
     */
    Optional<CambioSgc> findById(UUID id);

    /**
     * Listado paginado de Cambios_SGC del tenant con filtro opcional por estado
     * (Req 70.4, 12). El filtro nulo se ignora.
     *
     * @param estado   estado a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados.
     * @return la pagina de Cambios_SGC que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM CambioSgc c
            WHERE (:estado IS NULL OR c.estado = :estado)
            """)
    Page<CambioSgc> buscar(
            @Param("estado") EstadoCambioSgc estado,
            Pageable pageable);
}
