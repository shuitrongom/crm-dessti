package com.dessti.crm.rhnomina.nomina.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.rhnomina.nomina.domain.ReciboNomina;

/**
 * Repositorio Spring Data JPA de la entidad {@link ReciboNomina} (Req 41, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link ReciboNomina}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, reforzado por la Row-Level Security (Capa 2, V34).</p>
 */
public interface ReciboNominaRepository extends JpaRepository<ReciboNomina, UUID> {

    /**
     * Lista los Recibo_Nomina de una Nomina del tenant vigente para el Timbrado y el
     * calculo de totales (Req 41.4).
     *
     * @param nominaId identificador de la Nomina.
     * @return la lista de Recibo_Nomina de la Nomina (vacia si aun no se ha calculado).
     */
    List<ReciboNomina> findByNominaId(UUID nominaId);

    /**
     * Lista de forma paginada los Recibo_Nomina de una Nomina del tenant vigente
     * (Req 41.1).
     *
     * @param nominaId identificador de la Nomina.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Recibo_Nomina de la Nomina.
     */
    Page<ReciboNomina> findByNominaId(UUID nominaId, Pageable pageable);

    /**
     * Indica si una Nomina ya tiene Recibo_Nomina generados en el tenant vigente.
     * Evita recalcular una Nomina ya calculada (Req 41.1).
     *
     * @param nominaId identificador de la Nomina.
     * @return {@code true} si existe al menos un Recibo_Nomina de esa Nomina.
     */
    boolean existsByNominaId(UUID nominaId);

    /**
     * Agregacion de <strong>solo lectura</strong> del costo de nomina del periodo
     * (Req 22.1, 48.1): suma las {@code percepciones} de los Recibo_Nomina del tenant
     * vigente cuyo calculo ({@code created_at}) cae en el periodo. Las percepciones
     * representan el costo bruto de la nomina; se excluyen los recibos cancelados. El
     * filtro global de Hibernate y la RLS de V34 acotan la consulta al {@code tenant_id}
     * vigente (Req 23); no modifica dato alguno (Req 22.2).
     *
     * @param desde limite inferior de {@code created_at} (inclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta limite superior de {@code created_at} (exclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return el costo de nomina (percepciones) del periodo, o {@code 0}.
     */
    @Query("""
            SELECT COALESCE(SUM(r.percepciones), 0) FROM ReciboNomina r
            WHERE r.estado <> com.dessti.crm.rhnomina.nomina.domain.EstadoReciboNomina.CANCELADO
              AND r.createdAt >= :desde
              AND r.createdAt < :hasta
            """)
    BigDecimal sumarCostoNomina(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del neto pagado de nomina del periodo
     * (Req 22.1): suma el {@code neto} de los Recibo_Nomina no cancelados del tenant
     * vigente cuyo calculo cae en el periodo. Acotada al {@code tenant_id} vigente por el
     * filtro de Hibernate y la RLS (Req 23).
     *
     * @param desde limite inferior de {@code created_at} (inclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta limite superior de {@code created_at} (exclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return el neto pagado de nomina del periodo, o {@code 0}.
     */
    @Query("""
            SELECT COALESCE(SUM(r.neto), 0) FROM ReciboNomina r
            WHERE r.estado <> com.dessti.crm.rhnomina.nomina.domain.EstadoReciboNomina.CANCELADO
              AND r.createdAt >= :desde
              AND r.createdAt < :hasta
            """)
    BigDecimal sumarNetoNomina(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Recibo_Nomina no
     * cancelados del periodo (Req 22.1). Acotada al {@code tenant_id} vigente por el
     * filtro de Hibernate y la RLS (Req 23).
     *
     * @param desde limite inferior de {@code created_at} (inclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta limite superior de {@code created_at} (exclusivo); NO admite
     *              {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return el conteo de Recibo_Nomina del periodo.
     */
    @Query("""
            SELECT COUNT(r) FROM ReciboNomina r
            WHERE r.estado <> com.dessti.crm.rhnomina.nomina.domain.EstadoReciboNomina.CANCELADO
              AND r.createdAt >= :desde
              AND r.createdAt < :hasta
            """)
    long contarRecibos(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
