package com.dessti.crm.contabilidad.cxp.adapter.out.persistence;

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

import com.dessti.crm.contabilidad.cxp.domain.CuentaPorPagar;
import com.dessti.crm.contabilidad.cxp.domain.EstadoCuentaPorPagar;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link CuentaPorPagar}
 * (Req 42, 23). Como {@link CuentaPorPagar} extiende {@code TenantScopedEntity}, el
 * filtro global de Hibernate (Capa 1) y la RLS de V33 (Capa 2) acotan estas
 * consultas al tenant vigente (Req 23).
 */
public interface CuentaPorPagarRepository extends JpaRepository<CuentaPorPagar, UUID> {

    /**
     * Busca una Cuenta_Por_Pagar por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la CxP.
     * @return la CxP, o vacio (que la aplicacion traduce a 404).
     */
    Optional<CuentaPorPagar> findById(UUID id);

    /**
     * Busca la Cuenta_Por_Pagar asociada a una Factura_Proveedor dentro del tenant
     * vigente (una CxP por factura, UNIQUE en V33). Base de la idempotencia del
     * registro al conciliar (Req 42.1).
     *
     * @param facturaProveedorId Factura_Proveedor de origen.
     * @return la CxP de la factura, o vacio si no existe.
     */
    Optional<CuentaPorPagar> findByFacturaProveedorId(UUID facturaProveedorId);

    /**
     * Indica si ya existe una Cuenta_Por_Pagar para la Factura_Proveedor en el tenant
     * vigente (pre-check de idempotencia del Req 42.1).
     *
     * @param facturaProveedorId Factura_Proveedor de origen.
     * @return {@code true} si ya hay una CxP para esa factura.
     */
    boolean existsByFacturaProveedorId(UUID facturaProveedorId);

    /**
     * Cuentas_Por_Pagar del tenant vigente con saldo pendiente (estados
     * {@code pendiente} o {@code parcial}), opcionalmente filtradas por Proveedor,
     * para el calculo de la antiguedad de saldos (Req 42.5).
     *
     * @param proveedorId Proveedor a filtrar; {@code null} no filtra.
     * @return las CxP con saldo pendiente que cumplen el filtro.
     */
    @Query("""
            SELECT c FROM CuentaPorPagar c
            WHERE c.estado IN (
                    com.dessti.crm.contabilidad.cxp.domain.EstadoCuentaPorPagar.PENDIENTE,
                    com.dessti.crm.contabilidad.cxp.domain.EstadoCuentaPorPagar.PARCIAL)
              AND (:proveedorId IS NULL OR c.proveedorId = :proveedorId)
            """)
    List<CuentaPorPagar> buscarPendientesParaAging(@Param("proveedorId") UUID proveedorId);

    /**
     * Listado paginado de Cuentas_Por_Pagar del tenant vigente con filtros opcionales
     * por Proveedor y por estado (Req 42.6). Cada filtro nulo se ignora.
     *
     * @param proveedorId Proveedor a filtrar; {@code null} no filtra.
     * @param estado      estado a filtrar; {@code null} no filtra.
     * @param pageable    parametros de paginacion ya acotados (20/100).
     * @return la pagina de CxP que cumplen los filtros.
     */
    @Query("""
            SELECT c FROM CuentaPorPagar c
            WHERE (:proveedorId IS NULL OR c.proveedorId = :proveedorId)
              AND (:estado IS NULL OR c.estado = :estado)
            """)
    Page<CuentaPorPagar> buscarConFiltros(
            @Param("proveedorId") UUID proveedorId,
            @Param("estado") EstadoCuentaPorPagar estado,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> del saldo vencido de las
     * Cuentas_Por_Pagar del tenant vigente (Req 22.1, 48.1): suma el {@code saldo} de las
     * CxP con saldo pendiente ({@code pendiente}/{@code parcial}) cuya
     * {@code fecha_vencimiento} ya paso respecto a {@code referencia}. El filtro global de
     * Hibernate y la RLS de V33 acotan la consulta al {@code tenant_id} vigente (Req 23);
     * no modifica dato alguno (Req 22.2).
     *
     * @param referencia fecha de referencia (normalmente hoy); obligatoria.
     * @return el saldo total vencido por pagar, o {@code 0}.
     */
    @Query("""
            SELECT COALESCE(SUM(c.saldo), 0) FROM CuentaPorPagar c
            WHERE c.estado IN (
                    com.dessti.crm.contabilidad.cxp.domain.EstadoCuentaPorPagar.PENDIENTE,
                    com.dessti.crm.contabilidad.cxp.domain.EstadoCuentaPorPagar.PARCIAL)
              AND c.fechaVencimiento IS NOT NULL
              AND c.fechaVencimiento < :referencia
            """)
    BigDecimal sumarSaldoVencido(@Param("referencia") LocalDate referencia);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Cuentas_Por_Pagar
     * vencidas del tenant vigente (Req 22.1). Acotada al {@code tenant_id} vigente por el
     * filtro de Hibernate y la RLS (Req 23).
     *
     * @param referencia fecha de referencia (normalmente hoy); obligatoria.
     * @return el conteo de CxP vencidas del tenant.
     */
    @Query("""
            SELECT COUNT(c) FROM CuentaPorPagar c
            WHERE c.estado IN (
                    com.dessti.crm.contabilidad.cxp.domain.EstadoCuentaPorPagar.PENDIENTE,
                    com.dessti.crm.contabilidad.cxp.domain.EstadoCuentaPorPagar.PARCIAL)
              AND c.fechaVencimiento IS NOT NULL
              AND c.fechaVencimiento < :referencia
            """)
    long contarVencidas(@Param("referencia") LocalDate referencia);
}
