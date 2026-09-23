package com.dessti.crm.estrategia.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.estrategia.domain.ObjetivoEstrategico;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link ObjetivoEstrategico}
 * (Req 58, 23). Replica el patron de {@code OrdenFabricacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link ObjetivoEstrategico} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate {@code tenantFilter} (Capa 1) acota automaticamente estas consultas
 * al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V38) lo refuerza.
 * La busqueda por {@code id} de un objetivo de otro tenant devuelve vacio (que la
 * aplicacion traduce a 404 y audita, Req 23.3).</p>
 */
public interface ObjetivoEstrategicoRepository extends JpaRepository<ObjetivoEstrategico, UUID> {

    /**
     * Busca un objetivo por su identificador dentro del tenant vigente. Un objetivo
     * inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del objetivo.
     * @return el objetivo, o vacio.
     */
    Optional<ObjetivoEstrategico> findById(UUID id);

    /**
     * Listado paginado de Objetivos_Estrategicos del tenant vigente con filtros
     * opcionales por periodo y por responsable (Req 58.5, 58.6). Cada filtro nulo se
     * ignora (coincide con cualquier valor). El filtro por periodo selecciona los
     * objetivos <strong>vigentes</strong> en la fecha indicada, es decir aquellos
     * cuyo intervalo {@code [periodo_inicio, periodo_fin]} contiene esa fecha. El
     * filtro por responsable es una coincidencia exacta (sin distincion de
     * mayusculas/minusculas). Cuando ningun resultado coincide, la pagina es vacia
     * con {@code totalElements = 0}.
     *
     * @param enPeriodo    fecha para filtrar objetivos vigentes; {@code null} no filtra.
     * @param responsable  responsable a filtrar (exacto, case-insensitive);
     *                     {@code null}/blanco no filtra.
     * @param pageable     parametros de paginacion ya acotados (20/100).
     * @return la pagina de objetivos que cumplen los filtros.
     */
    // El bind :responsable se envuelve en CAST(... AS string) dentro de LOWER para que
    // Hibernate 6 emita "lower(cast(? as varchar))" y PostgreSQL infiera el tipo textual
    // del parametro. Sin el cast, cuando :responsable llega NULL (filtro ausente), el
    // planificador de PostgreSQL type-checkea LOWER(:responsable) ANTES del corto-circuito
    // ":responsable IS NULL", infiere el bind no tipado como bytea y falla con
    // "no existe la funcion lower(bytea)". El cast fuerza el tipo VARCHAR (columna
    // responsable, V38) y conserva la semantica: null/blanco no filtra, comparacion
    // exacta sin distincion de mayusculas/minusculas cuando se aporta valor.
    @Query("""
            SELECT o FROM ObjetivoEstrategico o
            WHERE (:enPeriodo IS NULL
                   OR (o.periodoInicio <= :enPeriodo AND o.periodoFin >= :enPeriodo))
              AND (:responsable IS NULL
                   OR LOWER(o.responsable) = LOWER(CAST(:responsable AS string)))
            """)
    Page<ObjetivoEstrategico> buscarConFiltros(
            @Param("enPeriodo") LocalDate enPeriodo,
            @Param("responsable") String responsable,
            Pageable pageable);

    /**
     * Agregacion de <strong>solo lectura</strong> del avance promedio de los
     * Objetivos_Estrategicos del tenant vigente (Req 22.1, 48.1), acotada de forma
     * opcional a los objetivos cuyo periodo <em>solapa</em> el rango indicado. El filtro
     * global de Hibernate y la RLS de V38 acotan la consulta al {@code tenant_id} vigente
     * (Req 23); no modifica dato alguno (Req 22.2).
     *
     * @param desde inicio del rango; NO admite {@code null}: el adaptador pasa
     *              {@code RangoPeriodo.FECHA_MINIMA} para "sin limite inferior".
     * @param hasta fin del rango; NO admite {@code null}: el adaptador pasa
     *              {@code RangoPeriodo.FECHA_MAXIMA} para "sin limite superior".
     * @return el avance promedio [0, 100], o {@code null} si no hay objetivos.
     */
    // Las cotas de fecha se comparan directamente (sin patron ":param IS NULL OR ...")
    // porque el adaptador SIEMPRE aporta valores no nulos: asi PostgreSQL infiere el tipo
    // del bind y se evita "could not determine data type of parameter". La semantica "sin
    // limite" se conserva con las cotas centinela de RangoPeriodo.
    @Query("""
            SELECT AVG(o.avance) FROM ObjetivoEstrategico o
            WHERE o.periodoFin >= :desde
              AND o.periodoInicio <= :hasta
            """)
    BigDecimal promediarAvance(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Objetivos_Estrategicos
     * del tenant vigente (Req 22.1), acotada de forma opcional al periodo que solapa el
     * rango. Acotada al {@code tenant_id} vigente por el filtro de Hibernate y la RLS.
     *
     * @param desde inicio del rango; NO admite {@code null}: el adaptador pasa
     *              {@code RangoPeriodo.FECHA_MINIMA} para "sin limite inferior".
     * @param hasta fin del rango; NO admite {@code null}: el adaptador pasa
     *              {@code RangoPeriodo.FECHA_MAXIMA} para "sin limite superior".
     * @return el conteo de objetivos del tenant.
     */
    // Cotas de fecha no nulas (ver promediarAvance): evita el fallo de inferencia de tipo
    // de PostgreSQL conservando la semantica "sin limite" via cotas centinela.
    @Query("""
            SELECT COUNT(o) FROM ObjetivoEstrategico o
            WHERE o.periodoFin >= :desde
              AND o.periodoInicio <= :hasta
            """)
    long contarObjetivos(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Agregacion de <strong>solo lectura</strong> del numero de Objetivos_Estrategicos
     * del tenant vigente con avance mayor o igual al umbral indicado (Req 22.1), acotada
     * de forma opcional al periodo que solapa el rango. Con umbral {@code 100} produce el
     * conteo de objetivos cumplidos. Acotada al {@code tenant_id} vigente por el filtro de
     * Hibernate y la RLS (Req 23).
     *
     * @param umbral avance minimo (inclusive); obligatorio.
     * @param desde  inicio del rango; NO admite {@code null}: el adaptador pasa
     *               {@code RangoPeriodo.FECHA_MINIMA} para "sin limite inferior".
     * @param hasta  fin del rango; NO admite {@code null}: el adaptador pasa
     *               {@code RangoPeriodo.FECHA_MAXIMA} para "sin limite superior".
     * @return el conteo de objetivos con avance &gt;= umbral.
     */
    // Cotas de fecha no nulas (ver promediarAvance): evita el fallo de inferencia de tipo
    // de PostgreSQL conservando la semantica "sin limite" via cotas centinela.
    @Query("""
            SELECT COUNT(o) FROM ObjetivoEstrategico o
            WHERE o.avance >= :umbral
              AND o.periodoFin >= :desde
              AND o.periodoInicio <= :hasta
            """)
    long contarConAvanceMinimo(
            @Param("umbral") BigDecimal umbral,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
