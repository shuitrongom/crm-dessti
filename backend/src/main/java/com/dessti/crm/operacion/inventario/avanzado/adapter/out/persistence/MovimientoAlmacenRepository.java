package com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.operacion.inventario.avanzado.domain.MovimientoAlmacen;

/**
 * Repositorio Spring Data JPA del Kardex APPEND-ONLY por Almacen {@link MovimientoAlmacen}
 * (Req 60, 23). En la tarea 23.1 solo se usa para LEER el Kardex cronologico; la escritura
 * de movimientos con costeo llega en la tarea 23.2.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> {@link MovimientoAlmacen} extiende
 * {@code TenantScopedEntity}; el filtro global de Hibernate y la RLS de V26 acotan estas
 * consultas al {@code tenant_id} vigente.</p>
 */
public interface MovimientoAlmacenRepository extends JpaRepository<MovimientoAlmacen, UUID> {

    /**
     * Lista de forma paginada el Kardex cronologico (por {@code created_at} ascendente)
     * de un Material en un Almacen dentro del tenant vigente, acotado por un rango de
     * fechas (Req 60).
     *
     * <p><strong>Cotas de fecha SIEMPRE tipadas (sin {@code ":param IS NULL OR ..."}):</strong>
     * los limites {@code desde}/{@code hasta} nunca son nulos aqui; la semantica "sin
     * limite" la aporta la capa de aplicacion sustituyendo un nulo por una cota centinela
     * ({@code RangoPeriodo.instanteDesdeOMinimo}/{@code instanteHastaOMaximo}). Asi
     * PostgreSQL puede inferir el tipo del bind y se evita el error
     * "could not determine data type of parameter" que produce un bind {@code timestamptz}
     * nulo. Es el mismo patron ya usado por el resto de repositorios del proyecto que
     * filtran por fecha.</p>
     *
     * @param almacenId  Almacen cuyo Kardex se consulta.
     * @param materialId Material cuyo Kardex se consulta.
     * @param desde      instante minimo (inclusive); cota centinela si no se filtra.
     * @param hasta      instante maximo (inclusive); cota centinela si no se filtra.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de movimientos del Kardex en orden cronologico ascendente.
     */
    @Query("""
            SELECT m FROM MovimientoAlmacen m
            WHERE m.almacenId = :almacenId
              AND m.materialId = :materialId
              AND m.createdAt >= :desde
              AND m.createdAt <= :hasta
            ORDER BY m.createdAt ASC
            """)
    Page<MovimientoAlmacen> buscarKardex(
            @Param("almacenId") UUID almacenId,
            @Param("materialId") UUID materialId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Pageable pageable);

    /**
     * Indica si existe algun movimiento de Kardex que referencie el Lote indicado
     * dentro del tenant vigente. Sustenta la baja SEGURA de un Lote (Req 60): un
     * Lote con historial de movimientos no puede eliminarse para preservar la
     * integridad contable del inventario.
     *
     * @param loteId identificador del Lote.
     * @return {@code true} si algun movimiento referencia el Lote.
     */
    boolean existsByLoteId(UUID loteId);

    /**
     * Agrega la EXISTENCIA VIVA POR LOTE de un Material dentro del tenant vigente (Req 60),
     * derivada del Kardex append-only: por cada movimiento con {@code lote_id}, suma la
     * cantidad de las ENTRADAS y las patas de ENTRADA de transferencia, y resta las SALIDAS
     * y las patas de SALIDA de transferencia. Los movimientos de ajuste por conteo fisico no
     * llevan lote (son a nivel Material/Almacen), por lo que no distorsionan este saldo.
     *
     * <p>El resultado se agrupa por {@code (lote_id, almacen_id)} para conocer cuanto queda
     * de cada Lote en cada Almacen. El filtro opcional por Almacen se aplica con una cota
     * null-safe: si {@code almacenId} es {@code null}, no restringe (patron
     * {@code CAST(:almacenId AS ...) IS NULL}). Acotada al tenant por el filtro de Hibernate
     * y la RLS (Req 23).</p>
     *
     * @param materialId Material cuyos Lotes se agregan; obligatorio.
     * @param almacenId  Almacen a filtrar; {@code null} agrega todos los Almacenes.
     * @return filas {@code [loteId, almacenId, cantidadNeta]} con solo movimientos con lote.
     */
    @Query("""
            SELECT m.loteId, m.almacenId,
                   COALESCE(SUM(CASE
                       WHEN m.tipo IN (com.dessti.crm.operacion.inventario.avanzado.domain.TipoMovimientoAlmacen.ENTRADA,
                                       com.dessti.crm.operacion.inventario.avanzado.domain.TipoMovimientoAlmacen.TRANSFERENCIA_ENTRADA)
                            THEN m.cantidad
                       WHEN m.tipo IN (com.dessti.crm.operacion.inventario.avanzado.domain.TipoMovimientoAlmacen.SALIDA,
                                       com.dessti.crm.operacion.inventario.avanzado.domain.TipoMovimientoAlmacen.TRANSFERENCIA_SALIDA)
                            THEN -m.cantidad
                       ELSE 0 END), 0)
            FROM MovimientoAlmacen m
            WHERE m.materialId = :materialId
              AND m.loteId IS NOT NULL
              AND (CAST(:almacenId AS uuid) IS NULL OR m.almacenId = :almacenId)
            GROUP BY m.loteId, m.almacenId
            """)
    java.util.List<Object[]> agregarExistenciaPorLote(
            @Param("materialId") UUID materialId,
            @Param("almacenId") UUID almacenId);
}
