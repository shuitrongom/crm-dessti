package com.dessti.crm.platform.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de acceso a la bitacora de auditoria (Req 10). Es un adaptador de
 * salida del servicio de auditoria.
 *
 * <p><strong>Append-only:</strong> aunque {@link JpaRepository} declara
 * operaciones de borrado/modificacion, el {@link ServicioAuditoria} solo usa
 * {@code save} (insercion) y las consultas de lectura definidas aqui. La
 * inmutabilidad efectiva la garantizan el trigger de BD y los permisos del rol
 * de aplicacion, con independencia de la superficie de la interfaz.</p>
 */
public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    /**
     * Devuelve el ultimo registro de la cadena global (mayor {@code id}), que
     * aporta el {@code hash_previo} para el siguiente registro (Req 10.7). Se
     * usa un bloqueo pesimista de escritura para serializar el avance de la
     * cabeza de la cadena frente a inserciones concurrentes.
     *
     * @return el registro con mayor {@code id}, o {@code null} si la bitacora
     *         esta vacia.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistroAuditoria r where r.id = (select max(r2.id) from RegistroAuditoria r2)")
    RegistroAuditoria bloquearUltimoRegistro();

    /**
     * Consulta filtrable por actor, tipo de recurso, rango de fechas y tenant
     * (Req 10.5). Cada criterio nulo se ignora (no restringe). El resultado se
     * ordena de forma estable segun el {@link Pageable} recibido (por defecto,
     * el servicio ordena por {@code id}).
     *
     * @param tenantId  tenant a filtrar; {@code null} para no filtrar por tenant.
     * @param actor     actor a filtrar; {@code null} para no filtrar.
     * @param recurso   tipo de recurso a filtrar; {@code null} para no filtrar.
     * @param desde     limite inferior (inclusive); {@code null} para no acotar.
     * @param hasta     limite superior (inclusive); {@code null} para no acotar.
     * @param pageable  parametros de paginacion y orden.
     * @return la pagina de registros que cumplen los criterios.
     */
    @Query("""
            select r from RegistroAuditoria r
            where (:tenantId is null or r.tenantId = :tenantId)
              and (:actor    is null or r.actor    = :actor)
              and (:recurso  is null or r.recurso  = :recurso)
              and (:desde    is null or r.timestampUtc >= :desde)
              and (:hasta    is null or r.timestampUtc <= :hasta)
            """)
    Page<RegistroAuditoria> buscar(
            @Param("tenantId") UUID tenantId,
            @Param("actor") String actor,
            @Param("recurso") String recurso,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Pageable pageable);

    /**
     * Variante no paginada de {@link #buscar} para la exportacion (Req 10.9).
     *
     * @param tenantId  tenant a filtrar; {@code null} para no filtrar por tenant.
     * @param actor     actor a filtrar; {@code null} para no filtrar.
     * @param recurso   tipo de recurso a filtrar; {@code null} para no filtrar.
     * @param desde     limite inferior (inclusive); {@code null} para no acotar.
     * @param hasta     limite superior (inclusive); {@code null} para no acotar.
     * @param sort      orden estable de la exportacion.
     * @return la lista completa de registros que cumplen los criterios.
     */
    @Query("""
            select r from RegistroAuditoria r
            where (:tenantId is null or r.tenantId = :tenantId)
              and (:actor    is null or r.actor    = :actor)
              and (:recurso  is null or r.recurso  = :recurso)
              and (:desde    is null or r.timestampUtc >= :desde)
              and (:hasta    is null or r.timestampUtc <= :hasta)
            """)
    List<RegistroAuditoria> exportar(
            @Param("tenantId") UUID tenantId,
            @Param("actor") String actor,
            @Param("recurso") String recurso,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Sort sort);

    /**
     * Devuelve una pagina de registros de la cadena global ordenada de forma
     * ascendente por {@code id}, acotada opcionalmente por un rango de id
     * (inclusive). Sustenta la <strong>verificacion de integridad de la cadena
     * bajo demanda</strong> (Req 10.13), permitiendo recorrer toda la bitacora
     * o un rango en lotes (paginacion) sin cargarla completa en memoria.
     *
     * <p>El orden ascendente por {@code id} es imprescindible: la cadena de hash
     * se encadena en ese orden (ver {@link ServicioAuditoria}).</p>
     *
     * @param idDesde  limite inferior de {@code id} (inclusive); {@code null}
     *                 para no acotar por el inicio.
     * @param idHasta  limite superior de {@code id} (inclusive); {@code null}
     *                 para no acotar por el final.
     * @param pageable paginacion del lote (el servicio impone el orden por id).
     * @return la pagina de registros del rango, ordenada ascendente por id.
     */
    @Query("""
            select r from RegistroAuditoria r
            where (:idDesde is null or r.id >= :idDesde)
              and (:idHasta is null or r.id <= :idHasta)
            order by r.id asc
            """)
    Page<RegistroAuditoria> recorrerCadenaPorId(
            @Param("idDesde") Long idDesde,
            @Param("idHasta") Long idHasta,
            Pageable pageable);

    /**
     * Cuenta los eventos de un tenant con una accion dada dentro de una ventana
     * temporal, para la deteccion de patrones sensibles de las alertas de
     * auditoria (Req 10.11). El limite {@code desde} es inclusive.
     *
     * <p>Para el ambito de plataforma ({@code tenant_id} nulo) se pasa
     * {@code tenantId = null} y se contabilizan los eventos con tenant nulo.</p>
     *
     * @param tenantId empresa del patron; {@code null} para el ambito de
     *                 plataforma (eventos con {@code tenant_id} nulo).
     * @param accion   accion a contar (por ejemplo {@code acceso_denegado}).
     * @param desde    inicio de la ventana temporal (inclusive).
     * @return numero de eventos que cumplen el patron en la ventana.
     */
    @Query("""
            select count(r) from RegistroAuditoria r
            where ((:tenantId is null and r.tenantId is null) or r.tenantId = :tenantId)
              and r.accion = :accion
              and r.timestampUtc >= :desde
            """)
    long contarPorAccionEnVentana(
            @Param("tenantId") UUID tenantId,
            @Param("accion") String accion,
            @Param("desde") Instant desde);
}
