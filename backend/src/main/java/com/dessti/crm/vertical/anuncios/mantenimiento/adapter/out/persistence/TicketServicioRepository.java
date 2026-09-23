package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.vertical.anuncios.mantenimiento.domain.EstadoTicketServicio;
import com.dessti.crm.vertical.anuncios.mantenimiento.domain.TicketServicio;

// Nota: la deteccion de vencimiento del SLA (Req 20.7) usa aritmetica de intervalos
// sobre TIMESTAMPTZ y una correlacion con el contrato; se resuelve con una consulta
// nativa (buscarConFiltrosVencidos) por portabilidad y claridad, dado que la suma de
// un intervalo variable a un TIMESTAMPTZ no es expresable de forma portable en JPQL.

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link TicketServicio}
 * (Req 20.2–20.7, 23). Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link TicketServicio} extiende {@code TenantScopedEntity}, el filtro global de
 * Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas
 * consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V27) lo
 * refuerza. La busqueda por {@code id} de un ticket de otro tenant devuelve vacio:
 * la capa de aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface TicketServicioRepository extends JpaRepository<TicketServicio, UUID> {

    /**
     * Busca un Ticket_Servicio por su identificador dentro del tenant vigente. Un
     * ticket inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del Ticket_Servicio.
     * @return el Ticket_Servicio, o vacio.
     */
    Optional<TicketServicio> findById(UUID id);

    /**
     * Listado paginado de Tickets_Servicio del tenant vigente con filtros
     * opcionales por estado y por Cliente (Req 20.7). Cada filtro nulo se ignora
     * (coincide con cualquier valor), de modo que sin filtros se devuelven todos
     * los tickets del tenant. Cuando ningun resultado coincide, la pagina
     * resultante es vacia con {@code totalElements = 0} (semantica de {@link Page}).
     *
     * <p>Es la variante SIN el filtro de vencimiento del SLA; la capa de aplicacion
     * la usa cuando {@code slaVencido} no esta activo. La variante con vencimiento
     * es {@link #buscarVencidos(String, UUID, Instant, Pageable)}.</p>
     *
     * @param estado    estado a filtrar; {@code null} no filtra por estado.
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Tickets_Servicio que cumplen los filtros.
     */
    @Query("""
            SELECT t FROM TicketServicio t
            WHERE (:estado IS NULL OR t.estado = :estado)
              AND (:clienteId IS NULL OR t.clienteId = :clienteId)
            """)
    Page<TicketServicio> buscarConFiltros(
            @Param("estado") EstadoTicketServicio estado,
            @Param("clienteId") UUID clienteId,
            Pageable pageable);

    /**
     * Listado paginado de los Tickets_Servicio del tenant vigente con el SLA de
     * resolucion <strong>vencido</strong> (Req 20.7), con filtros opcionales
     * adicionales por estado (etiqueta) y por Cliente.
     *
     * <h2>Definicion de "SLA vencido"</h2>
     * <p>Un ticket esta vencido cuando aun NO se ha resuelto ni cerrado (estados
     * {@code abierto}, {@code asignado}, {@code en_proceso}), TIENE un
     * Contrato_Mantenimiento asociado y el tiempo transcurrido desde la apertura ya
     * supera el tiempo de resolucion del SLA del contrato:
     * {@code ahora - abierto_en > sla_resolucion_horas}. La comparacion suma un
     * intervalo de {@code sla_resolucion_horas} horas al instante de apertura y lo
     * contrasta con {@code ahora} (instante de referencia del {@code Clock}
     * inyectado, pasado como parametro para determinismo). Los tickets sin contrato
     * nunca se consideran vencidos (no hay SLA contra el que evaluar).</p>
     *
     * <p>Se implementa como consulta <strong>nativa</strong> por portabilidad: la
     * suma de un intervalo <em>variable</em> (dependiente de la columna
     * {@code sla_resolucion_horas}) a un {@code TIMESTAMPTZ} no es expresable de
     * forma portable en JPQL. La consulta respeta igualmente la RLS de V27 (Capa 2)
     * y el filtro por tenant, pues opera sobre las mismas tablas. El {@code estado}
     * se compara como etiqueta ASCII (columna {@code estado}); {@code null}/blanco
     * no filtra por estado.</p>
     *
     * @param estado    etiqueta de estado a filtrar; {@code null} no filtra.
     * @param clienteId Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param ahora     instante UTC de referencia para evaluar el vencimiento.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Tickets_Servicio con el SLA de resolucion vencido.
     */
    @Query(value = """
            SELECT t.* FROM ticket_servicio t
            JOIN contrato_mantenimiento c ON c.id = t.contrato_mantenimiento_id
            WHERE t.estado NOT IN ('resuelto', 'cerrado')
              AND (:estado IS NULL OR t.estado = :estado)
              AND (:clienteId IS NULL OR t.cliente_id = :clienteId)
              AND :ahora > t.abierto_en + make_interval(hours => c.sla_resolucion_horas)
            """,
            countQuery = """
            SELECT count(*) FROM ticket_servicio t
            JOIN contrato_mantenimiento c ON c.id = t.contrato_mantenimiento_id
            WHERE t.estado NOT IN ('resuelto', 'cerrado')
              AND (:estado IS NULL OR t.estado = :estado)
              AND (:clienteId IS NULL OR t.cliente_id = :clienteId)
              AND :ahora > t.abierto_en + make_interval(hours => c.sla_resolucion_horas)
            """,
            nativeQuery = true)
    Page<TicketServicio> buscarVencidos(
            @Param("estado") String estado,
            @Param("clienteId") UUID clienteId,
            @Param("ahora") Instant ahora,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> del cumplimiento del SLA de los
     * Tickets_Servicio resueltos del tenant vigente (Req 22.1): cuenta los tickets con
     * el SLA de resolucion evaluado ({@code sla_resolucion_cumplido} no nulo, es decir
     * con contrato) discriminando por si se cumplio o no, acotado por el instante de
     * resolucion. El filtro global de Hibernate y la RLS acotan la consulta al
     * {@code tenant_id} vigente (Req 23); no modifica dato alguno (Req 22.2).
     *
     * @param cumplido {@code true} para los que cumplieron el SLA de resolucion;
     *                 {@code false} para los que lo incumplieron.
     * @param desde    limite inferior de {@code resuelto_en} (inclusivo); NO admite
     *                 {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta    limite superior de {@code resuelto_en} (exclusivo); NO admite
     *                 {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return el conteo de tickets resueltos con contrato segun el cumplimiento.
     */
    @Query("""
            SELECT COUNT(t) FROM TicketServicio t
            WHERE t.slaResolucionCumplido = :cumplido
              AND t.resueltoEn >= :desde
              AND t.resueltoEn < :hasta
            """)
    long contarPorCumplimientoSlaResolucion(
            @Param("cumplido") boolean cumplido,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del cumplimiento del SLA de
     * <em>respuesta</em> de los Tickets_Servicio resueltos del tenant vigente
     * (Req 22.1): cuenta los tickets con {@code sla_respuesta_cumplido} igual al valor
     * indicado, acotado por el instante de resolucion. El aislamiento por tenant lo
     * garantizan el filtro de Hibernate y la RLS (Req 23).
     *
     * @param cumplido {@code true} para los que cumplieron el SLA de respuesta;
     *                 {@code false} para los que lo incumplieron.
     * @param desde    limite inferior de {@code resuelto_en} (inclusivo); NO admite
     *                 {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MINIMO}.
     * @param hasta    limite superior de {@code resuelto_en} (exclusivo); NO admite
     *                 {@code null}: el adaptador pasa {@code RangoPeriodo.INSTANTE_MAXIMO}.
     * @return el conteo de tickets resueltos con contrato segun el cumplimiento de
     *         respuesta.
     */
    @Query("""
            SELECT COUNT(t) FROM TicketServicio t
            WHERE t.slaRespuestaCumplido = :cumplido
              AND t.resueltoEn >= :desde
              AND t.resueltoEn < :hasta
            """)
    long contarPorCumplimientoSlaRespuesta(
            @Param("cumplido") boolean cumplido,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
