package com.dessti.crm.calidad.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.calidad.domain.AccionCorrectiva;
import com.dessti.crm.calidad.domain.EstadoAccionCorrectiva;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link AccionCorrectiva}
 * (Req 70.2, 23). Provee el listado paginado con filtros (Req 12) y agregaciones de
 * solo lectura para los indicadores de calidad (Req 70.7): conteo por estado y tiempos
 * de cierre.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de Hibernate
 * y la RLS de PostgreSQL (V47) acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface AccionCorrectivaRepository extends JpaRepository<AccionCorrectiva, UUID> {

    /**
     * Busca una Accion_Correctiva por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Accion_Correctiva.
     * @return la Accion_Correctiva, o vacio (404 en la aplicacion, Req 23.3).
     */
    Optional<AccionCorrectiva> findById(UUID id);

    /**
     * Listado paginado de Acciones_Correctivas del tenant con filtros opcionales por
     * estado y No_Conformidad (Req 70.2, 12). Cada filtro nulo se ignora.
     *
     * @param estado          estado a filtrar; {@code null} no filtra.
     * @param noConformidadId No_Conformidad a filtrar; {@code null} no filtra.
     * @param pageable        parametros de paginacion ya acotados.
     * @return la pagina de Acciones_Correctivas que cumplen los filtros.
     */
    @Query("""
            SELECT a FROM AccionCorrectiva a
            WHERE (:estado IS NULL OR a.estado = :estado)
              AND (:noConformidadId IS NULL OR a.noConformidadId = :noConformidadId)
            """)
    Page<AccionCorrectiva> buscar(
            @Param("estado") EstadoAccionCorrectiva estado,
            @Param("noConformidadId") UUID noConformidadId,
            Pageable pageable);

    /**
     * Cuenta las Acciones_Correctivas del tenant en un estado dado (indicador de
     * calidad, Req 70.7).
     *
     * @param estado estado a contar.
     * @return el numero de Acciones_Correctivas en ese estado.
     */
    long countByEstado(EstadoAccionCorrectiva estado);

    /**
     * Devuelve, para las Acciones_Correctivas cerradas del tenant, el numero de segundos
     * transcurridos entre su alta ({@code created_at}) y su cierre ({@code cerrada_en}),
     * base del indicador de tiempo medio de cierre (Req 70.7). Solo incluye acciones con
     * marca de cierre. El calculo del promedio se hace en el dominio.
     *
     * @return lista de duraciones de cierre en segundos (una por Accion_Correctiva cerrada).
     */
    @Query("""
            SELECT (EXTRACT(EPOCH FROM a.cerradaEn) - EXTRACT(EPOCH FROM a.createdAt))
            FROM AccionCorrectiva a
            WHERE a.estado = com.dessti.crm.calidad.domain.EstadoAccionCorrectiva.CERRADA
              AND a.cerradaEn IS NOT NULL
            """)
    List<Double> segundosDeCierreDeCerradas();

    /**
     * Devuelve las No_Conformidades del tenant que tienen mas de una Accion_Correctiva
     * asociada (reincidencia de una misma No_Conformidad, Req 70.7). El numerador de la
     * tasa de reincidencia es el tamano de esta lista; se calcula en el dominio para
     * evitar subconsultas derivadas no soportadas por JPQL.
     *
     * @return identificadores de No_Conformidad con mas de una Accion_Correctiva.
     */
    @Query("""
            SELECT a.noConformidadId
            FROM AccionCorrectiva a
            WHERE a.noConformidadId IS NOT NULL
            GROUP BY a.noConformidadId
            HAVING COUNT(a) > 1
            """)
    List<UUID> noConformidadesReincidentes();

    /**
     * Cuenta cuantas No_Conformidades distintas del tenant tienen al menos una
     * Accion_Correctiva asociada (denominador de la tasa de reincidencia, Req 70.7).
     *
     * @return el numero de No_Conformidades distintas con Accion_Correctiva.
     */
    @Query("""
            SELECT COUNT(DISTINCT a.noConformidadId)
            FROM AccionCorrectiva a
            WHERE a.noConformidadId IS NOT NULL
            """)
    long contarNoConformidadesConAccion();
}
