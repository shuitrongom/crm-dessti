package com.dessti.crm.platform.empresas;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Plan} (Req 25).
 *
 * <p>En la tarea 14.1 se usaba como <em>referencia de solo lectura</em> para
 * validar el Plan inicial de una Empresa (Req 24.2). La tarea 14.2 lo emplea
 * ademas para la gestion completa de Planes por el {@code super_admin}: alta,
 * actualizacion y consulta, con la unicidad de nombre respaldada por
 * {@code uq_plan_nombre} (V1).</p>
 */
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    /**
     * Comprueba si ya existe un Plan con ese nombre. Sirve para anticipar el
     * conflicto de unicidad (Req 25.1) antes de confiar en el indice unico
     * {@code uq_plan_nombre} de V1. La comparacion es sensible a
     * mayusculas/minusculas: el nombre se almacena tal como se recorta en la
     * entidad.
     *
     * @param nombre nombre del Plan a comprobar.
     * @return {@code true} si ya existe un Plan con ese nombre.
     */
    boolean existsByNombre(String nombre);
}
