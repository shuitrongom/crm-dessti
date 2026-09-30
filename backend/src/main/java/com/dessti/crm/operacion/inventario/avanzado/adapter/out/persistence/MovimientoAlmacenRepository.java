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
}
