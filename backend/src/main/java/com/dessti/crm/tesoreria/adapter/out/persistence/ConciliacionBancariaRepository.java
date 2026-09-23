package com.dessti.crm.tesoreria.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.tesoreria.domain.ConciliacionBancaria;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link ConciliacionBancaria}
 * (Req 43, 23). Como {@link ConciliacionBancaria} extiende {@code TenantScopedEntity},
 * el filtro global de Hibernate (Capa 1) y la RLS de V35 (Capa 2) acotan estas
 * consultas al tenant vigente (Req 23).
 */
public interface ConciliacionBancariaRepository
        extends JpaRepository<ConciliacionBancaria, UUID> {

    /**
     * Listado paginado de Conciliacion_Bancaria del tenant vigente con filtros
     * opcionales por Cuenta_Bancaria, periodo (rango de fechas de la conciliacion) y
     * estado (Req 43.6). Cada filtro nulo se ignora.
     *
     * @param cuentaBancariaId Cuenta_Bancaria a filtrar; {@code null} no filtra.
     * @param desde            instante minimo (inclusivo) de la conciliacion; se
     *                         compara contra {@code fecha}. {@code null} no filtra.
     * @param hasta            instante maximo (inclusivo) de la conciliacion;
     *                         {@code null} no filtra.
     * @param estado           etiqueta del estado (en_proceso/completa); {@code null} no filtra.
     * @param pageable         parametros de paginacion ya acotados (20/100).
     * @return la pagina de conciliaciones que cumplen los filtros.
     */
    @Query("""
            SELECT c FROM ConciliacionBancaria c
            WHERE (:cuentaBancariaId IS NULL OR c.cuentaBancariaId = :cuentaBancariaId)
              AND (:desde IS NULL OR c.fecha >= :desde)
              AND (:hasta IS NULL OR c.fecha <= :hasta)
              AND (:estado IS NULL OR c.estado = :estado)
            """)
    Page<ConciliacionBancaria> buscarConFiltros(
            @Param("cuentaBancariaId") UUID cuentaBancariaId,
            @Param("desde") java.time.Instant desde,
            @Param("hasta") java.time.Instant hasta,
            @Param("estado")
            com.dessti.crm.tesoreria.domain.EstadoConciliacionBancaria estado,
            Pageable pageable);
}
