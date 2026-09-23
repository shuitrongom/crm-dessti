package com.dessti.crm.contabilidad.cxc.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.contabilidad.cxc.domain.CuentaPorCobrar;
import com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link CuentaPorCobrar}
 * (Req 36, 23). Como {@link CuentaPorCobrar} extiende {@code TenantScopedEntity}, el
 * filtro global de Hibernate (Capa 1) y la RLS de V31 (Capa 2) acotan estas
 * consultas al tenant vigente (Req 23).
 */
public interface CuentaPorCobrarRepository extends JpaRepository<CuentaPorCobrar, UUID> {

    /**
     * Busca una Cuenta_Por_Cobrar por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la CxC.
     * @return la CxC, o vacio (que la aplicacion traduce a 404).
     */
    Optional<CuentaPorCobrar> findById(UUID id);

    /**
     * Busca la Cuenta_Por_Cobrar asociada a una Factura dentro del tenant vigente
     * (una CxC por Factura, UNIQUE en V31). Base de la idempotencia del registro
     * (Req 36.1) y de la disminucion por Nota de Credito (Req 37.1).
     *
     * @param facturaId Factura de origen.
     * @return la CxC de la Factura, o vacio si no existe.
     */
    Optional<CuentaPorCobrar> findByFacturaId(UUID facturaId);

    /**
     * Indica si ya existe una Cuenta_Por_Cobrar para la Factura en el tenant vigente
     * (pre-check de idempotencia del Req 36.1).
     *
     * @param facturaId Factura de origen.
     * @return {@code true} si ya hay una CxC para esa Factura.
     */
    boolean existsByFacturaId(UUID facturaId);

    /**
     * Cuentas_Por_Cobrar del tenant vigente con saldo pendiente (estados
     * {@code pendiente} o {@code parcial}), opcionalmente filtradas por Cliente, para
     * el calculo de la antiguedad de saldos (Req 36.5).
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @return las CxC con saldo pendiente que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM CuentaPorCobrar c
            WHERE c.estado IN (
                    com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar.PENDIENTE,
                    com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar.PARCIAL)
              AND (:clienteId IS NULL OR c.clienteId = :clienteId)
            """)
    List<CuentaPorCobrar> buscarPendientesParaAging(@Param("clienteId") UUID clienteId);

    /**
     * Listado paginado de Cuentas_Por_Cobrar del tenant vigente con filtros
     * opcionales por Cliente y por estado (Req 36.6). Cada filtro nulo se ignora.
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param estado    estado a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de CxC que cumplen los filtros.
     */
    @Query("""
            SELECT c FROM CuentaPorCobrar c
            WHERE (:clienteId IS NULL OR c.clienteId = :clienteId)
              AND (:estado IS NULL OR c.estado = :estado)
            """)
    Page<CuentaPorCobrar> buscarConFiltros(
            @Param("clienteId") UUID clienteId,
            @Param("estado") EstadoCuentaPorCobrar estado,
            Pageable pageable);

    /**
     * Cuentas_Por_Cobrar de un Cliente cuya fecha de emision cae en el intervalo UTC
     * {@code [desde, hasta)}, ordenadas por fecha de emision, para el
     * <strong>estado de cuenta por Cliente</strong> de los reportes financieros
     * (Req 39.1, 39.3). Es una consulta de <strong>solo lectura</strong> que no
     * modifica los datos de origen (Req 39.2). Cada limite de fecha es opcional.
     *
     * @param clienteId Cliente cuyo estado de cuenta se consulta; obligatorio.
     * @param desde     instante minimo (inclusivo) de {@code fecha_emision}; {@code null} no filtra.
     * @param hasta     instante maximo (exclusivo) de {@code fecha_emision}; {@code null} no filtra.
     * @return las CxC del Cliente en el periodo, ordenadas por fecha de emision.
     */
    @Query("""
            SELECT c FROM CuentaPorCobrar c
            WHERE c.clienteId = :clienteId
              AND (:desde IS NULL OR c.fechaEmision >= :desde)
              AND (:hasta IS NULL OR c.fechaEmision < :hasta)
            ORDER BY c.fechaEmision
            """)
    List<CuentaPorCobrar> buscarEstadoCuentaCliente(
            @Param("clienteId") UUID clienteId,
            @Param("desde") java.time.Instant desde,
            @Param("hasta") java.time.Instant hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del saldo vencido de las
     * Cuentas_Por_Cobrar del tenant vigente (Req 22.1, 48.1): suma el {@code saldo} de las
     * CxC con saldo pendiente ({@code pendiente}/{@code parcial}) cuya
     * {@code fecha_vencimiento} ya paso respecto a {@code referencia}. El filtro global de
     * Hibernate y la RLS de V31 acotan la consulta al {@code tenant_id} vigente (Req 23);
     * no modifica dato alguno (Req 22.2).
     *
     * @param referencia fecha de referencia (normalmente hoy) para considerar vencida una
     *                   CxC; obligatoria.
     * @return el saldo total vencido, o {@code 0} si no hay CxC vencidas.
     */
    @Query("""
            SELECT COALESCE(SUM(c.saldo), 0) FROM CuentaPorCobrar c
            WHERE c.estado IN (
                    com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar.PENDIENTE,
                    com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar.PARCIAL)
              AND c.fechaVencimiento IS NOT NULL
              AND c.fechaVencimiento < :referencia
            """)
    BigDecimal sumarSaldoVencido(@Param("referencia") LocalDate referencia);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Cuentas_Por_Cobrar
     * vencidas del tenant vigente (Req 22.1): cuenta las CxC con saldo pendiente cuya
     * {@code fecha_vencimiento} ya paso respecto a {@code referencia}. Acotada al
     * {@code tenant_id} vigente por el filtro de Hibernate y la RLS (Req 23).
     *
     * @param referencia fecha de referencia (normalmente hoy); obligatoria.
     * @return el conteo de CxC vencidas del tenant.
     */
    @Query("""
            SELECT COUNT(c) FROM CuentaPorCobrar c
            WHERE c.estado IN (
                    com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar.PENDIENTE,
                    com.dessti.crm.contabilidad.cxc.domain.EstadoCuentaPorCobrar.PARCIAL)
              AND c.fechaVencimiento IS NOT NULL
              AND c.fechaVencimiento < :referencia
            """)
    long contarVencidas(@Param("referencia") LocalDate referencia);
}
