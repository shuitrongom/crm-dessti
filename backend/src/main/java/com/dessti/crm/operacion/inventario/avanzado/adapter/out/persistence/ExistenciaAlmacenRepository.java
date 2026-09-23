package com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.operacion.inventario.avanzado.domain.ExistenciaAlmacen;

/**
 * Repositorio Spring Data JPA del saldo de existencias por Almacen
 * {@link ExistenciaAlmacen} (Req 60, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> {@link ExistenciaAlmacen}
 * extiende {@code TenantScopedEntity}; el filtro global de Hibernate y la RLS de V26
 * acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface ExistenciaAlmacenRepository extends JpaRepository<ExistenciaAlmacen, UUID> {

    /**
     * Busca el saldo de un Material en un Almacen dentro del tenant vigente (relacion
     * unica por (Almacen, Material), Req 60). Vacio si aun no existe saldo.
     *
     * @param almacenId  Almacen del saldo.
     * @param materialId Material del saldo.
     * @return el saldo, o vacio.
     */
    Optional<ExistenciaAlmacen> findByAlmacenIdAndMaterialId(UUID almacenId, UUID materialId);

    /**
     * Listado paginado de saldos de existencias del tenant vigente con filtros
     * opcionales por Almacen y por Material (Req 60). Cada filtro nulo se ignora.
     *
     * @param almacenId  Almacen a filtrar; {@code null} no filtra.
     * @param materialId Material a filtrar; {@code null} no filtra.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de saldos que cumplen los filtros.
     */
    @Query("""
            SELECT e FROM ExistenciaAlmacen e
            WHERE (:almacenId IS NULL OR e.almacenId = :almacenId)
              AND (:materialId IS NULL OR e.materialId = :materialId)
            """)
    Page<ExistenciaAlmacen> buscarConFiltros(
            @Param("almacenId") UUID almacenId,
            @Param("materialId") UUID materialId,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> de la valuacion total del inventario
     * del tenant vigente (Req 22.1, 60): suma {@code cantidad * costo_promedio} de todos
     * los saldos. El filtro global de Hibernate y la RLS de V26 acotan la consulta al
     * {@code tenant_id} vigente (Req 23); no modifica dato alguno (Req 22.2).
     *
     * @return la valuacion total del inventario, o {@code 0} si no hay saldos.
     */
    @Query("""
            SELECT COALESCE(SUM(e.cantidad * e.costoPromedio), 0) FROM ExistenciaAlmacen e
            """)
    BigDecimal sumarValuacionTotal();

    /**
     * Agregacion de <strong>solo lectura</strong> de las existencias y su valuacion por
     * Almacen del tenant vigente (Req 22.1, 60): agrupa por {@code almacen_id} sumando la
     * cantidad total y la valuacion ({@code cantidad * costo_promedio}). Acotada al
     * {@code tenant_id} vigente por el filtro de Hibernate y la RLS (Req 23).
     *
     * @return tripletas {@code [almacenId, cantidadTotal, valuacion]} por Almacen.
     */
    @Query("""
            SELECT e.almacenId, COALESCE(SUM(e.cantidad), 0),
                   COALESCE(SUM(e.cantidad * e.costoPromedio), 0)
            FROM ExistenciaAlmacen e
            GROUP BY e.almacenId
            """)
    List<Object[]> resumirPorAlmacen();

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Almacenes con saldo del
     * tenant vigente (Req 22.1). Acotada al {@code tenant_id} vigente por el filtro de
     * Hibernate y la RLS (Req 23).
     *
     * @return el conteo de Almacenes distintos con existencias registradas.
     */
    @Query("""
            SELECT COUNT(DISTINCT e.almacenId) FROM ExistenciaAlmacen e
            """)
    long contarAlmacenesConExistencias();
}
