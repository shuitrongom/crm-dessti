package com.dessti.crm.comercial.actividad.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.comercial.actividad.domain.Actividad;
import com.dessti.crm.comercial.actividad.domain.EstadoActividad;
import com.dessti.crm.comercial.actividad.domain.TipoActividad;

/**
 * Repositorio Spring Data JPA de la entidad {@link Actividad} (V79).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Actividad}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V79) lo refuerza. La
 * busqueda por {@code id} de una Actividad de otro tenant devuelve vacio: la capa
 * de aplicacion lo traduce a 404 y audita el intento (Req 4.3, 23.3).</p>
 */
public interface ActividadRepository extends JpaRepository<Actividad, UUID> {

    /**
     * Busca una Actividad por su identificador dentro del tenant vigente. Una
     * Actividad inexistente o de otro tenant produce {@link Optional#empty()} (que
     * la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Actividad.
     * @return la Actividad, o vacio.
     */
    Optional<Actividad> findById(UUID id);

    /**
     * Listado paginado de Actividades del tenant vigente con filtros opcionales
     * por Cliente, Oportunidad, tipo, estado y responsable. Cada filtro nulo se
     * ignora (coincide con cualquier valor). El orden lo aporta el {@code Pageable}
     * (el listado del timeline se solicita por {@code fechaProgramada} descendente).
     *
     * @param clienteId     Cliente a filtrar; {@code null} no filtra.
     * @param oportunidadId Oportunidad a filtrar; {@code null} no filtra.
     * @param tipo          tipo a filtrar; {@code null} no filtra.
     * @param estado        estado a filtrar; {@code null} no filtra.
     * @param responsableId Usuario responsable a filtrar; {@code null} no filtra.
     * @param pageable      parametros de paginacion y orden ya acotados (20/100).
     * @return la pagina de Actividades que cumplen los filtros.
     */
    @Query("""
            SELECT a FROM Actividad a
            WHERE (:clienteId IS NULL OR a.clienteId = :clienteId)
              AND (:oportunidadId IS NULL OR a.oportunidadId = :oportunidadId)
              AND (:tipo IS NULL OR a.tipo = :tipo)
              AND (:estado IS NULL OR a.estado = :estado)
              AND (:responsableId IS NULL OR a.responsableUsuarioId = :responsableId)
            """)
    Page<Actividad> buscarConFiltros(
            @Param("clienteId") UUID clienteId,
            @Param("oportunidadId") UUID oportunidadId,
            @Param("tipo") TipoActividad tipo,
            @Param("estado") EstadoActividad estado,
            @Param("responsableId") UUID responsableId,
            Pageable pageable);
}
