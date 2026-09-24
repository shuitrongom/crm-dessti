package com.dessti.crm.contabilidad.polizas.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.contabilidad.polizas.domain.PolizaContable;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link PolizaContable}
 * (Req 38, 23). Como {@link PolizaContable} extiende {@code TenantScopedEntity}, el
 * filtro global de Hibernate (Capa 1) y la RLS de V33 (Capa 2) acotan estas
 * consultas al tenant vigente (Req 23).
 */
public interface PolizaContableRepository extends JpaRepository<PolizaContable, UUID> {

    /**
     * Busca una Poliza_Contable por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la poliza.
     * @return la poliza, o vacio (que la aplicacion traduce a 404).
     */
    Optional<PolizaContable> findById(UUID id);

    /**
     * Listado paginado de Polizas_Contables del tenant vigente con filtros opcionales
     * por rango de fechas y por Cuenta_Contable (Req 38.6). Cada filtro nulo se
     * ignora. El filtro por Cuenta_Contable se resuelve por existencia de un renglon
     * ({@code movimiento_poliza}) que afecte a esa cuenta.
     *
     * @param desde            fecha minima (inclusiva); {@code null} no filtra.
     * @param hasta            fecha maxima (inclusiva); {@code null} no filtra.
     * @param cuentaContableId Cuenta_Contable a filtrar; {@code null} no filtra.
     * @param pageable         parametros de paginacion ya acotados (20/100).
     * @return la pagina de polizas que cumplen los filtros.
     */
    @Query("""
            SELECT p FROM PolizaContable p
            WHERE (:desde IS NULL OR p.fecha >= :desde)
              AND (:hasta IS NULL OR p.fecha <= :hasta)
              AND (:cuentaContableId IS NULL OR EXISTS (
                    SELECT 1 FROM PolizaContable p2 JOIN p2.renglones r
                    WHERE p2 = p AND r.cuentaContableId = :cuentaContableId))
            """)
    Page<PolizaContable> buscarConFiltros(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("cuentaContableId") UUID cuentaContableId,
            Pageable pageable);

    /**
     * Lista TODAS las Polizas_Contables del tenant vigente cuya fecha cae en el
     * periodo {@code [desde, hasta]} (ambos inclusivos), ordenadas por fecha e id de
     * forma determinista, con sus renglones. Sirve a la Contabilidad Electronica
     * (Anexo 24) para emitir el XML de polizas del periodo.
     *
     * @param desde fecha minima (inclusiva) de la poliza.
     * @param hasta fecha maxima (inclusiva) de la poliza.
     * @return las polizas del periodo, ordenadas por fecha e id.
     */
    @Query("""
            SELECT DISTINCT p FROM PolizaContable p
            WHERE p.fecha >= :desde AND p.fecha <= :hasta
            ORDER BY p.fecha ASC, p.id ASC
            """)
    java.util.List<PolizaContable> listarPorPeriodo(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
