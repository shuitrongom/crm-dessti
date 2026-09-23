package com.dessti.crm.vertical.anuncios.permiso.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.vertical.anuncios.permiso.domain.EstadoPermisoInstalacion;
import com.dessti.crm.vertical.anuncios.permiso.domain.PermisoInstalacion;
import com.dessti.crm.vertical.anuncios.permiso.domain.TipoPermisoInstalacion;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link PermisoInstalacion}
 * (Req 17, 23). Replica el patron de {@code LevantamientoSitioRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link PermisoInstalacion} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas
 * consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V20) lo
 * refuerza. La busqueda por {@code id} de un permiso de otro tenant devuelve vacio:
 * la capa de aplicacion lo traduce a 404 y audita el intento (Req 23.3).</p>
 */
public interface PermisoInstalacionRepository extends JpaRepository<PermisoInstalacion, UUID> {

    /**
     * Busca un Permiso_Instalacion por su identificador dentro del tenant vigente.
     * Uno inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador del Permiso_Instalacion.
     * @return el Permiso_Instalacion, o vacio.
     */
    Optional<PermisoInstalacion> findById(UUID id);

    /**
     * Indica si el Sitio dado tiene al menos un Permiso_Instalacion en el estado
     * indicado dentro del tenant vigente. Con {@link EstadoPermisoInstalacion#APROBADO}
     * implementa la guarda de programacion de instalacion (Req 17.4): la instalacion
     * de un Sitio solo puede programarse si esta consulta devuelve {@code true}. La
     * consume el bloque 22 via {@code PermisoAprobadoPort}.
     *
     * @param sitioId Sitio a verificar.
     * @param estado  estado a comprobar.
     * @return {@code true} si el Sitio tiene un permiso en ese estado.
     */
    boolean existsBySitioIdAndEstado(UUID sitioId, EstadoPermisoInstalacion estado);

    /**
     * Indica si un Permiso_Instalacion concreto esta en el estado indicado dentro
     * del tenant vigente. Alternativa a {@link #existsBySitioIdAndEstado} cuando la
     * guarda se expresa por identificador de permiso (Req 17.4).
     *
     * @param id     identificador del Permiso_Instalacion.
     * @param estado estado a comprobar.
     * @return {@code true} si el permiso existe (en el tenant) y esta en ese estado.
     */
    boolean existsByIdAndEstado(UUID id, EstadoPermisoInstalacion estado);

    /**
     * Listado paginado de permisos del tenant vigente con filtros opcionales por
     * Sitio, tipo y estado (Req 17.6). Cada filtro nulo se ignora (coincide con
     * cualquier valor), de modo que sin filtros se devuelven todos los permisos del
     * tenant. Cuando ningun resultado coincide, la pagina resultante es vacia con
     * {@code totalElements = 0}.
     *
     * @param sitioId  Sitio a filtrar; {@code null} no filtra por Sitio.
     * @param tipo     tipo a filtrar; {@code null} no filtra por tipo.
     * @param estado   estado a filtrar; {@code null} no filtra por estado.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de permisos que cumplen los filtros.
     */
    @Query("""
            SELECT p FROM PermisoInstalacion p
            WHERE (:sitioId IS NULL OR p.sitioId = :sitioId)
              AND (:tipo IS NULL OR p.tipo = :tipo)
              AND (:estado IS NULL OR p.estado = :estado)
            """)
    Page<PermisoInstalacion> buscarConFiltros(
            @Param("sitioId") UUID sitioId,
            @Param("tipo") TipoPermisoInstalacion tipo,
            @Param("estado") EstadoPermisoInstalacion estado,
            Pageable pageable);

    /**
     * Devuelve los permisos <strong>aprobados</strong> del tenant vigente cuya
     * {@code fecha_vencimiento} cae dentro del rango {@code [desde, hasta]}, ambos
     * inclusive. Da soporte a la notificacion de vencimiento proximo del Req 17.5:
     * la capa de aplicacion invoca esta consulta con {@code desde = hoy} y
     * {@code hasta = hoy + 30 dias} (segun el {@link java.time.Clock} inyectado)
     * para localizar los permisos aprobados que venceran en los proximos 30 dias.
     *
     * @param desde  limite inferior del rango (inclusive), tipicamente hoy.
     * @param hasta  limite superior del rango (inclusive), tipicamente hoy + 30 dias.
     * @return los permisos aprobados que vencen dentro del rango, ordenados por
     *         fecha de vencimiento ascendente.
     */
    @Query("""
            SELECT p FROM PermisoInstalacion p
            WHERE p.estado = com.dessti.crm.vertical.anuncios.permiso.domain.EstadoPermisoInstalacion.APROBADO
              AND p.fechaVencimiento >= :desde
              AND p.fechaVencimiento <= :hasta
            ORDER BY p.fechaVencimiento ASC
            """)
    List<PermisoInstalacion> buscarAprobadosVenciendoEntre(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
