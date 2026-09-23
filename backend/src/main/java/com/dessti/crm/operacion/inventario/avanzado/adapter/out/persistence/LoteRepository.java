package com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.operacion.inventario.avanzado.domain.Lote;

/**
 * Repositorio Spring Data JPA de los Lotes {@link Lote} (Req 60, 23). El uso efectivo
 * de los Lotes en los movimientos por Almacen y en las capas de costo PEPS llega en la
 * tarea 23.2; aqui se define para el alta basica y las consultas por Material.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> {@link Lote} extiende
 * {@code TenantScopedEntity}; el filtro global de Hibernate y la RLS de V26 acotan estas
 * consultas al {@code tenant_id} vigente.</p>
 */
public interface LoteRepository extends JpaRepository<Lote, UUID> {

    /**
     * Lista los Lotes de un Material dentro del tenant vigente (Req 60).
     *
     * @param materialId identificador del Material.
     * @return los Lotes del Material (posiblemente vacio).
     */
    List<Lote> findByMaterialId(UUID materialId);

    /**
     * Busca un Lote de un Material por su codigo dentro del tenant vigente (codigo unico
     * por Material, Req 60). Util para evitar duplicados en el alta.
     *
     * @param materialId identificador del Material.
     * @param codigo     codigo del Lote.
     * @return el Lote, o vacio.
     */
    Optional<Lote> findByMaterialIdAndCodigo(UUID materialId, String codigo);
}
