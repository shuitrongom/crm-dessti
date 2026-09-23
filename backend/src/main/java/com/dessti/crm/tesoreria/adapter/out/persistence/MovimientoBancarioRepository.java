package com.dessti.crm.tesoreria.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.tesoreria.domain.MovimientoBancario;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link MovimientoBancario}
 * (Req 43, 23). Como {@link MovimientoBancario} extiende {@code TenantScopedEntity},
 * el filtro global de Hibernate (Capa 1) y la RLS de V35 (Capa 2) acotan estas
 * consultas al tenant vigente (Req 23).
 */
public interface MovimientoBancarioRepository
        extends JpaRepository<MovimientoBancario, UUID> {

    /**
     * Listado paginado de Movimiento_Bancario del tenant vigente con filtros
     * opcionales por Cuenta_Bancaria, periodo (rango de fechas) y estado de
     * conciliacion (Req 43.6). Cada filtro nulo se ignora. El estado se filtra por su
     * etiqueta de base de datos ({@code pendiente}/{@code conciliado}/{@code excepcion}).
     *
     * @param cuentaBancariaId Cuenta_Bancaria a filtrar; {@code null} no filtra.
     * @param desde            fecha minima (inclusiva); {@code null} no filtra.
     * @param hasta            fecha maxima (inclusiva); {@code null} no filtra.
     * @param estadoConciliacion etiqueta del estado de conciliacion; {@code null} no filtra.
     * @param pageable         parametros de paginacion ya acotados (20/100).
     * @return la pagina de movimientos que cumplen los filtros.
     */
    @Query("""
            SELECT m FROM MovimientoBancario m
            WHERE (:cuentaBancariaId IS NULL OR m.cuentaBancariaId = :cuentaBancariaId)
              AND (:desde IS NULL OR m.fecha >= :desde)
              AND (:hasta IS NULL OR m.fecha <= :hasta)
              AND (:estadoConciliacion IS NULL OR m.estadoConciliacion = :estadoConciliacion)
            """)
    Page<MovimientoBancario> buscarConFiltros(
            @Param("cuentaBancariaId") UUID cuentaBancariaId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("estadoConciliacion")
            com.dessti.crm.tesoreria.domain.EstadoConciliacionMovimiento estadoConciliacion,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> del saldo bancario acumulado del tenant
     * vigente (Req 22.1, 48.1): suma el {@code monto} con signo (+ deposito / - retiro) de
     * todos los Movimiento_Bancario importados, acotado de forma opcional por rango de
     * fechas. El filtro global de Hibernate y la RLS de V35 acotan la consulta al
     * {@code tenant_id} vigente (Req 23); no modifica dato alguno (Req 22.2).
     *
     * @param desde fecha minima (inclusiva) del movimiento; NO admite {@code null}: el
     *              adaptador pasa {@code RangoPeriodo.FECHA_MINIMA} para "sin limite inferior".
     * @param hasta fecha maxima (inclusiva) del movimiento; NO admite {@code null}: el
     *              adaptador pasa {@code RangoPeriodo.FECHA_MAXIMA} para "sin limite superior".
     * @return el saldo bancario acumulado del tenant, o {@code 0}.
     */
    // Las cotas de fecha se comparan directamente (sin patron ":param IS NULL OR ...")
    // porque el adaptador SIEMPRE aporta valores no nulos: asi PostgreSQL infiere el tipo
    // del bind y se evita "could not determine data type of parameter". La semantica "sin
    // limite" se conserva con las cotas centinela de RangoPeriodo.
    @Query("""
            SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoBancario m
            WHERE m.fecha >= :desde
              AND m.fecha <= :hasta
            """)
    BigDecimal sumarSaldoBancario(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Movimiento_Bancario
     * pendientes de conciliar del tenant vigente (Req 22.1): cuenta los movimientos en
     * estado {@code pendiente}, acotado de forma opcional por rango de fechas. Acotada al
     * {@code tenant_id} vigente por el filtro de Hibernate y la RLS (Req 23).
     *
     * @param desde fecha minima (inclusiva); NO admite {@code null}: el adaptador pasa
     *              {@code RangoPeriodo.FECHA_MINIMA} para "sin limite inferior".
     * @param hasta fecha maxima (inclusiva); NO admite {@code null}: el adaptador pasa
     *              {@code RangoPeriodo.FECHA_MAXIMA} para "sin limite superior".
     * @return el conteo de partidas pendientes de conciliacion.
     */
    // Cotas de fecha no nulas (ver sumarSaldoBancario): evita el fallo de inferencia de
    // tipo de PostgreSQL conservando la semantica "sin limite" via cotas centinela.
    @Query("""
            SELECT COUNT(m) FROM MovimientoBancario m
            WHERE m.estadoConciliacion = com.dessti.crm.tesoreria.domain.EstadoConciliacionMovimiento.PENDIENTE
              AND m.fecha >= :desde
              AND m.fecha <= :hasta
            """)
    long contarPendientesConciliacion(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
