package com.dessti.crm.comercial.producto.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.comercial.producto.domain.ListaPrecios;

/**
 * Repositorio Spring Data JPA de la entidad {@link ListaPrecios} (Req 59, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> las consultas quedan
 * acotadas al tenant vigente por el filtro global de Hibernate (Capa 1) y la RLS
 * (Capa 2, V12).</p>
 */
public interface ListaPreciosRepository extends JpaRepository<ListaPrecios, UUID> {

    /**
     * Busca una Lista_Precios <strong>activa</strong> por id dentro del tenant
     * vigente. Inexistente, inactiva o de otro tenant produce
     * {@link Optional#empty()} (que la aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Lista_Precios.
     * @return la Lista_Precios activa, o vacio.
     */
    Optional<ListaPrecios> findByIdAndActivoTrue(UUID id);

    /**
     * Listado paginado de Listas_Precios <strong>activas</strong> del tenant
     * vigente cuyo nombre contiene el criterio indicado, sin distinguir
     * mayusculas (Req 59.7). Un {@code criterio} en blanco lista todas.
     *
     * @param criterio subcadena a buscar en el nombre (en minusculas).
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Listas_Precios activas que cumplen el filtro.
     */
    @Query("""
            SELECT l FROM ListaPrecios l
            WHERE l.activo = true
              AND LOWER(l.nombre) LIKE CONCAT('%', :criterio, '%')
            """)
    Page<ListaPrecios> buscarActivasPorNombre(@Param("criterio") String criterio, Pageable pageable);
}
