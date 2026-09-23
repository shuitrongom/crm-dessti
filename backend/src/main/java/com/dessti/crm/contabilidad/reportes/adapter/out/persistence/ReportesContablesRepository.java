package com.dessti.crm.contabilidad.reportes.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.contabilidad.polizas.domain.PolizaContable;

/**
 * Repositorio Spring Data JPA de <strong>solo lectura</strong> que agrega los
 * saldos por Cuenta_Contable a partir de las Polizas_Contables de un periodo, para
 * derivar los estados financieros y el balance general (Req 47.1, 47.3). No expone
 * ninguna operacion de escritura: es una vista de agregacion que no modifica los
 * datos de origen (Req 47.2).
 *
 * <p>Extiende {@code JpaRepository<PolizaContable, ...>} unicamente para reutilizar
 * la infraestructura de consulta del agregado ya mapeado; sus metodos son consultas
 * JPQL de agregacion. Como {@code PolizaContable}, {@code MovimientoPoliza} y
 * {@code CuentaContable} extienden {@code TenantScopedEntity}, el filtro global de
 * Hibernate (Capa 1) y la RLS de V33 (Capa 2) acotan estas consultas al tenant
 * vigente (Req 23).</p>
 */
public interface ReportesContablesRepository
        extends JpaRepository<PolizaContable, java.util.UUID> {

    /**
     * Agrega, por Cuenta_Contable, los totales de cargos y abonos de los renglones
     * de las Polizas_Contables cuya fecha cae en el periodo {@code [desde, hasta]}
     * (ambos inclusivos, cada limite opcional), junto con la clasificacion contable
     * de la cuenta (Req 47.1). Es la base de la balanza de comprobacion, el balance
     * general y el estado de resultados del periodo.
     *
     * <p>Solo se incluyen cuentas con al menos un movimiento en el periodo. El
     * resultado se ordena por codigo de cuenta para una presentacion estable.</p>
     *
     * @param desde fecha minima (inclusiva) de la poliza; {@code null} no filtra.
     * @param hasta fecha maxima (inclusiva) de la poliza; {@code null} no filtra.
     * @return los saldos agregados por cuenta del periodo (solo lectura).
     */
    @Query("""
            SELECT c.id AS cuentaId,
                   c.codigo AS codigo,
                   c.nombre AS nombre,
                   c.tipo AS tipo,
                   c.naturaleza AS naturaleza,
                   SUM(m.cargo) AS cargos,
                   SUM(m.abono) AS abonos
            FROM PolizaContable p
            JOIN p.renglones m
            JOIN CuentaContable c ON c.id = m.cuentaContableId
            WHERE (:desde IS NULL OR p.fecha >= :desde)
              AND (:hasta IS NULL OR p.fecha <= :hasta)
            GROUP BY c.id, c.codigo, c.nombre, c.tipo, c.naturaleza
            ORDER BY c.codigo
            """)
    List<SaldoCuentaProjection> agregarSaldosPorCuenta(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
