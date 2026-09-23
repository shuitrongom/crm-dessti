package com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.operacion.inventario.avanzado.domain.CapaCosto;

/**
 * Repositorio Spring Data JPA de las capas de costo PEPS {@link CapaCosto} (Req 60, 23).
 *
 * <p><strong>Alcance:</strong> se define en la tarea 23.1 para que la tarea 23.2 solo
 * agregue la logica del motor PEPS (creacion de capas al recibir entradas y consumo
 * ordenado por {@code secuencia} al registrar salidas). La consulta ordenada
 * {@link #findByAlmacenIdAndMaterialIdOrderBySecuenciaAsc(UUID, UUID)} devuelve las capas
 * en el orden de consumo FIFO; la persistencia usa {@code save} heredado.</p>
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> {@link CapaCosto} extiende
 * {@code TenantScopedEntity}; el filtro global de Hibernate y la RLS de V26 acotan estas
 * consultas al {@code tenant_id} vigente.</p>
 */
public interface CapaCostoRepository extends JpaRepository<CapaCosto, UUID> {

    /**
     * Devuelve las capas de costo de un Material en un Almacen, dentro del tenant
     * vigente, ordenadas por {@code secuencia} ascendente (orden de consumo PEPS, Req 60).
     * La tarea 23.2 las consumira en este orden.
     *
     * @param almacenId  Almacen de las capas.
     * @param materialId Material de las capas.
     * @return las capas ordenadas por secuencia ascendente (posiblemente vacio).
     */
    List<CapaCosto> findByAlmacenIdAndMaterialIdOrderBySecuenciaAsc(UUID almacenId, UUID materialId);

    /**
     * Devuelve la {@code secuencia} maxima de las capas de un Material en un Almacen dentro
     * del tenant vigente, o {@code null} si aun no existe ninguna capa (Req 60, tarea 23.2).
     * El servicio obtiene la siguiente secuencia monotonica como {@code MAX + 1}, de modo
     * que las capas conserven el orden de llegada para el consumo PEPS (DECISION 23.2).
     *
     * @param almacenId  Almacen de las capas.
     * @param materialId Material de las capas.
     * @return la secuencia maxima, o {@code null} si no hay capas.
     */
    @Query("""
            SELECT MAX(c.secuencia) FROM CapaCosto c
            WHERE c.almacenId = :almacenId
              AND c.materialId = :materialId
            """)
    Long maxSecuencia(@Param("almacenId") UUID almacenId, @Param("materialId") UUID materialId);
}
