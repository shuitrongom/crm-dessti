package com.dessti.crm.rhnomina.empleado.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.rhnomina.empleado.domain.Incidencia;

/**
 * Repositorio Spring Data JPA de la entidad {@link Incidencia} (Req 40.3, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link Incidencia}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota automaticamente estas consultas al
 * {@code tenant_id} vigente, reforzado por la Row-Level Security (Capa 2, V32).</p>
 */
public interface IncidenciaRepository extends JpaRepository<Incidencia, UUID> {

    /**
     * Lista de forma paginada el historico de Incidencia de un Empleado del tenant
     * vigente (Req 40.3, 40.4).
     *
     * @param empleadoId identificador del Empleado.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de Incidencia del Empleado.
     */
    Page<Incidencia> findByEmpleadoId(UUID empleadoId, Pageable pageable);

    /**
     * Lista las Incidencia de un Empleado en un Periodo_Nomina concreto del tenant
     * vigente. Sustenta el calculo de Nomina (Req 41.1): la nomina agrega, por
     * ejemplo, el tiempo extra registrado por el Empleado en el periodo.
     *
     * @param empleadoId    identificador del Empleado.
     * @param periodoNomina codigo del Periodo_Nomina en formato {@code AAAA-MM}.
     * @return la lista de Incidencia del Empleado en ese periodo (vacia si no hay).
     */
    List<Incidencia> findByEmpleadoIdAndPeriodoNomina(UUID empleadoId, String periodoNomina);
}
