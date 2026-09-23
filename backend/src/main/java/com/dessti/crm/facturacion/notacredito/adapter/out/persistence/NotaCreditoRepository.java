package com.dessti.crm.facturacion.notacredito.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.facturacion.notacredito.domain.EstadoNotaCredito;
import com.dessti.crm.facturacion.notacredito.domain.NotaCredito;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link NotaCredito} (Req 37,
 * 23). Como {@link NotaCredito} extiende {@code TenantScopedEntity}, el filtro
 * global de Hibernate (Capa 1) y la RLS de V30 (Capa 2) acotan estas consultas al
 * tenant vigente (Req 23).
 */
public interface NotaCreditoRepository extends JpaRepository<NotaCredito, UUID> {

    /**
     * Busca una Nota de Credito por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Nota de Credito.
     * @return la Nota de Credito, o vacio (que la aplicacion traduce a 404).
     */
    Optional<NotaCredito> findById(UUID id);

    /**
     * Suma el monto de las notas de credito <strong>no canceladas</strong>
     * ({@code borrador} + {@code timbrada}) que referencian la Factura dada, dentro
     * del tenant vigente (Req 37.2). Es la base del calculo del saldo disponible de
     * la Factura para notas de credito: {@code saldo = total - Σ notas previas}. Sin
     * notas previas devuelve {@code 0}.
     *
     * @param facturaId Factura referenciada.
     * @return la suma de montos de las notas de credito previas no canceladas (>= 0).
     */
    @Query("""
            SELECT COALESCE(SUM(n.monto), 0)
            FROM NotaCredito n
            WHERE n.facturaId = :facturaId
              AND n.estado <> com.dessti.crm.facturacion.notacredito.domain.EstadoNotaCredito.CANCELADA
            """)
    BigDecimal sumarMontoPorFactura(@Param("facturaId") UUID facturaId);

    /**
     * Listado paginado de Notas de Credito del tenant vigente con filtros opcionales
     * por Factura y por estado. Cada filtro nulo se ignora.
     *
     * @param facturaId Factura a filtrar; {@code null} no filtra.
     * @param estado    estado a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Notas de Credito que cumplen los filtros.
     */
    @Query("""
            SELECT n FROM NotaCredito n
            WHERE (:facturaId IS NULL OR n.facturaId = :facturaId)
              AND (:estado IS NULL OR n.estado = :estado)
            """)
    Page<NotaCredito> buscarConFiltros(
            @Param("facturaId") UUID facturaId,
            @Param("estado") EstadoNotaCredito estado,
            Pageable pageable);
}
