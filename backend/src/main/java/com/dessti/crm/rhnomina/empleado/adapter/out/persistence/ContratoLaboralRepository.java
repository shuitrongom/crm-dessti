package com.dessti.crm.rhnomina.empleado.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.rhnomina.empleado.domain.ContratoLaboral;

/**
 * Repositorio Spring Data JPA de la entidad {@link ContratoLaboral} (Req 40.1, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link ContratoLaboral}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota automaticamente estas consultas al
 * {@code tenant_id} vigente, reforzado por la Row-Level Security (Capa 2, V32).</p>
 */
public interface ContratoLaboralRepository extends JpaRepository<ContratoLaboral, UUID> {

    /**
     * Lista de forma paginada el historico de Contrato_Laboral de un Empleado del
     * tenant vigente (Req 40.4).
     *
     * @param empleadoId identificador del Empleado titular.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de Contrato_Laboral del Empleado.
     */
    Page<ContratoLaboral> findByEmpleadoId(UUID empleadoId, Pageable pageable);

    /**
     * Lista los Contrato_Laboral <strong>activos</strong> de un Empleado del tenant
     * vigente. Sustenta el calculo de Nomina (Req 41.1): la nomina toma el salario
     * diario y la periodicidad del contrato vigente del Empleado.
     *
     * @param empleadoId identificador del Empleado titular.
     * @return la lista de Contrato_Laboral activos del Empleado (vacia si no tiene).
     */
    List<ContratoLaboral> findByEmpleadoIdAndActivoTrue(UUID empleadoId);
}
