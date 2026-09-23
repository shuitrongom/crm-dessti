package com.dessti.crm.platform.empresas;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link PaqueteSuscripcion} (Req 3).
 *
 * <p>Espeja el patron de {@link PlanRepository}: es el acceso al catalogo de
 * <strong>plataforma</strong> de Paquetes de Suscripcion (dato NO tenant-scoped,
 * <strong>sin RLS</strong>) administrado por el {@code super_admin}. Sirve para el
 * alta, la actualizacion y la consulta paginada de Paquetes, con la unicidad de
 * nombre respaldada por {@code uq_paquete_suscripcion_nombre} (V64).</p>
 *
 * <p>Las operaciones de listado paginado ({@link JpaRepository#findAll(org.springframework.data.domain.Pageable)})
 * y de resolucion por identificadores ({@link JpaRepository#findAllById(Iterable)})
 * se heredan de {@link JpaRepository} (Req 10.3, 12.6).</p>
 */
public interface PaqueteSuscripcionRepository extends JpaRepository<PaqueteSuscripcion, UUID> {

    /**
     * Comprueba si ya existe un Paquete de Suscripcion con ese nombre. Sirve para
     * anticipar el conflicto de unicidad (Req 3.1) antes de confiar en el indice
     * unico {@code uq_paquete_suscripcion_nombre} de V64. La comparacion es
     * sensible a mayusculas/minusculas: el nombre se almacena tal como se recorta
     * en la entidad.
     *
     * @param nombre nombre del Paquete a comprobar.
     * @return {@code true} si ya existe un Paquete de Suscripcion con ese nombre.
     */
    boolean existsByNombre(String nombre);
}
