package com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.OrdenTrabajoInstalacion;

/**
 * Repositorio Spring Data JPA de la raiz del agregado
 * {@link OrdenTrabajoInstalacion} (Req 19, 23). Replica el patron de
 * {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link OrdenTrabajoInstalacion} extiende {@code TenantScopedEntity}, el filtro
 * global de Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em>
 * estas consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2,
 * V24) lo refuerza. La busqueda por {@code id} de una OTI de otro tenant devuelve
 * vacio: la capa de aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface OrdenTrabajoInstalacionRepository
        extends JpaRepository<OrdenTrabajoInstalacion, UUID> {

    /**
     * Busca una Orden_Trabajo_Instalacion por su identificador dentro del tenant
     * vigente. Una OTI inexistente o de otro tenant produce {@link Optional#empty()}
     * (que la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Orden_Trabajo_Instalacion.
     * @return la Orden_Trabajo_Instalacion, o vacio.
     */
    Optional<OrdenTrabajoInstalacion> findById(UUID id);

    /**
     * Indica si el Sitio dado tiene al menos una Orden_Trabajo_Instalacion en el
     * estado indicado dentro del tenant vigente. Con
     * {@link EstadoOrdenTrabajoInstalacion#COMPLETADA} implementa la consulta de la
     * fase de instalacion completada por Sitio (Req 21.3), que consume el submodulo
     * proyecto via {@code InstalacionCompletadaPort}. La consulta ya esta acotada
     * al tenant vigente por el filtro global de Hibernate y la RLS (Req 23).
     *
     * @param sitioId Sitio a verificar.
     * @param estado  estado a comprobar.
     * @return {@code true} si el Sitio tiene una OTI en ese estado.
     */
    boolean existsBySitioIdAndEstado(UUID sitioId, EstadoOrdenTrabajoInstalacion estado);

    /**
     * Indica si el Sitio dado tiene al menos una Orden_Trabajo_Instalacion (en
     * cualquier estado) dentro del tenant vigente. Sustenta la derivacion de la
     * fase Orden_Fabricacion <em>respaldada</em> por Sitio (Req 21.3): dado que una
     * OTI solo puede programarse a partir de una Orden_Fabricacion terminada
     * (Req 19.1/19.2), la existencia de una OTI para el Sitio <strong>implica</strong>
     * que una Orden_Fabricacion terminada la respalda. Se usa asi para evitar
     * inventar una columna {@code of.sitio_id} inexistente. La consulta ya esta
     * acotada al tenant vigente por el filtro global de Hibernate y la RLS (Req 23).
     *
     * @param sitioId Sitio a verificar.
     * @return {@code true} si el Sitio tiene al menos una OTI.
     */
    boolean existsBySitioId(UUID sitioId);

    /**
     * Listado paginado de Ordenes de Trabajo de Instalacion del tenant vigente con
     * filtros opcionales por estado, Cuadrilla y Cliente (Req 19.7). Cada filtro
     * nulo se ignora (coincide con cualquier valor), de modo que sin filtros se
     * devuelven todas las OTI del tenant. Cuando ningun resultado coincide, la
     * pagina resultante es vacia con {@code totalElements = 0} (semantica de
     * {@link Page}, Req 19.7).
     *
     * @param estado      estado a filtrar; {@code null} no filtra por estado.
     * @param cuadrillaId Cuadrilla a filtrar; {@code null} no filtra por Cuadrilla.
     * @param clienteId   Cliente a filtrar; {@code null} no filtra por Cliente.
     * @param pageable    parametros de paginacion ya acotados (20/100).
     * @return la pagina de Ordenes de Trabajo de Instalacion que cumplen los filtros.
     */
    @Query("""
            SELECT o FROM OrdenTrabajoInstalacion o
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:cuadrillaId IS NULL OR o.cuadrillaId = :cuadrillaId)
              AND (:clienteId IS NULL OR o.clienteId = :clienteId)
            """)
    Page<OrdenTrabajoInstalacion> buscarConFiltros(
            @Param("estado") EstadoOrdenTrabajoInstalacion estado,
            @Param("cuadrillaId") UUID cuadrillaId,
            @Param("clienteId") UUID clienteId,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> del cumplimiento de fechas
     * programadas de instalacion (Req 22.1): cuenta las OTI {@code completada} del
     * tenant vigente cuya fecha de conclusion (la marca {@code updated_at} del cierre)
     * cae dentro ({@code enFecha = true}) o fuera ({@code enFecha = false}) de la fecha
     * programada. El periodo se aplica sobre {@code fecha_programada}. El filtro global
     * de Hibernate y la RLS acotan la consulta al {@code tenant_id} vigente (Req 23);
     * no modifica dato alguno (Req 22.2).
     *
     * @param enFecha {@code true} para contar las completadas en o antes de la fecha
     *                programada; {@code false} para las completadas despues.
     * @param desde   limite inferior de {@code fecha_programada} (inclusivo); NO admite
     *                {@code null}: el adaptador pasa {@code RangoPeriodo.FECHA_MINIMA} para
     *                "sin limite inferior".
     * @param hasta   limite superior de {@code fecha_programada} (inclusivo); NO admite
     *                {@code null}: el adaptador pasa {@code RangoPeriodo.FECHA_MAXIMA} para
     *                "sin limite superior".
     * @return el conteo de OTI completadas segun el cumplimiento indicado.
     */
    // Las cotas de fecha se comparan directamente (sin patron ":param IS NULL OR ...")
    // porque el adaptador SIEMPRE aporta valores no nulos: asi PostgreSQL infiere el tipo
    // del bind y se evita "could not determine data type of parameter". La semantica "sin
    // limite" se conserva con las cotas centinela de RangoPeriodo.
    @Query("""
            SELECT COUNT(o) FROM OrdenTrabajoInstalacion o
            WHERE o.estado = com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion.COMPLETADA
              AND ((:enFecha = TRUE AND CAST(o.updatedAt AS date) <= o.fechaProgramada)
                OR (:enFecha = FALSE AND CAST(o.updatedAt AS date) > o.fechaProgramada))
              AND o.fechaProgramada >= :desde
              AND o.fechaProgramada <= :hasta
            """)
    long contarCompletadasPorCumplimiento(
            @Param("enFecha") boolean enFecha,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> de las OTI vencidas (Req 22.1):
     * cuenta las OTI del tenant vigente aun no finalizadas (estado
     * {@code programada} o {@code en_curso}) cuya {@code fecha_programada} ya paso
     * respecto a {@code referencia}, dentro del periodo indicado. El aislamiento por
     * tenant lo garantizan el filtro de Hibernate y la RLS (Req 23).
     *
     * @param referencia fecha de referencia (normalmente hoy) para considerar vencida
     *                   una OTI; obligatoria.
     * @param desde      limite inferior de {@code fecha_programada} (inclusivo); NO admite
     *                   {@code null}: el adaptador pasa {@code RangoPeriodo.FECHA_MINIMA}
     *                   para "sin limite inferior".
     * @param hasta      limite superior de {@code fecha_programada} (inclusivo); NO admite
     *                   {@code null}: el adaptador pasa {@code RangoPeriodo.FECHA_MAXIMA}
     *                   para "sin limite superior".
     * @return el conteo de OTI programadas/en curso ya vencidas.
     */
    // Cotas de fecha no nulas (ver contarCompletadasPorCumplimiento): evita el fallo de
    // inferencia de tipo de PostgreSQL conservando la semantica "sin limite" via cotas
    // centinela.
    @Query("""
            SELECT COUNT(o) FROM OrdenTrabajoInstalacion o
            WHERE o.estado IN (com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion.PROGRAMADA,
                               com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion.EN_CURSO)
              AND o.fechaProgramada < :referencia
              AND o.fechaProgramada >= :desde
              AND o.fechaProgramada <= :hasta
            """)
    long contarVencidas(
            @Param("referencia") LocalDate referencia,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
