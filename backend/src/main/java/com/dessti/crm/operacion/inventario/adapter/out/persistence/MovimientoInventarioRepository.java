package com.dessti.crm.operacion.inventario.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.operacion.inventario.domain.MovimientoInventario;

/**
 * Repositorio Spring Data JPA del historial APPEND-ONLY {@link MovimientoInventario}
 * (Req 18.2, 18.6, 18.7, 23). Solo se usa para <em>agregar</em> movimientos y
 * <em>listar</em> el historial por Material; nunca para actualizar ni borrar (Req 18.7).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> {@link MovimientoInventario}
 * extiende {@code TenantScopedEntity}; el filtro global de Hibernate y la RLS de V18
 * acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, UUID> {

    /**
     * Lista de forma paginada el historial de movimientos de un Material dentro del
     * tenant vigente (Req 18.6). Sin coincidencias devuelve una pagina vacia con total 0.
     *
     * @param materialId Material cuyo historial se consulta.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de movimientos del Material.
     */
    Page<MovimientoInventario> findByMaterialId(UUID materialId, Pageable pageable);
}
