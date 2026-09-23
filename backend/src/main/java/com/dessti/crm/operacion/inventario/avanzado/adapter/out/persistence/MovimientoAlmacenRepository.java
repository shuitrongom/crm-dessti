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
     * de un Material en un Almacen dentro del tenant vigente, con rango de fechas opcional
     * (Req 60). Los limites nulos no restringen:
     *
     * <ul>
     *   <li>{@code desde} nulo: sin cota inferior de fecha.</li>
     *   <li>{@code hasta} nulo: sin cota superior de fecha.</li>
     * </ul>
     *
     * @param almacenId  Almacen cuyo Kardex se consulta.
     * @param materialId Material cuyo Kardex se consulta.
     * @param desde      instante minimo (inclusive); {@code null} no filtra.
     * @param hasta      instante maximo (inclusive); {@code null} no filtra.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de movimientos del Kardex en orden cronologico ascendente.
     */
    @Query("""
            SELECT m FROM MovimientoAlmacen m
            WHERE m.almacenId = :almacenId
              AND m.materialId = :materialId
              AND (:desde IS NULL OR m.createdAt >= :desde)
              AND (:hasta IS NULL OR m.createdAt <= :hasta)
            ORDER BY m.createdAt ASC
            """)
    Page<MovimientoAlmacen> buscarKardex(
            @Param("almacenId") UUID almacenId,
            @Param("materialId") UUID materialId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Pageable pageable);
}
