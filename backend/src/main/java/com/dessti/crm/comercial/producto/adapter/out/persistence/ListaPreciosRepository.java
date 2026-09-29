package com.dessti.crm.comercial.producto.adapter.out.persistence;

import java.util.List;
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

    /**
     * Candidatas a solapamiento: Listas_Precios <strong>activas</strong> del
     * tenant vigente que comparten <em>alcance</em> con el segmento indicado y
     * que no son la propia lista, para aplicarles el predicado de dominio
     * {@link ListaPrecios#seSolapaCon(ListaPrecios)} en la regla de no
     * coexistencia de vigencias solapadas (Req 59.11).
     *
     * <p>El "mismo alcance" se resuelve aqui: si {@code segmento} es
     * {@code null} (lista general) se traen las otras generales; si tiene valor,
     * las del mismo segmento sin distinguir mayusculas. La decision final de
     * solapamiento de rangos la toma el dominio (fuente unica de la regla), por
     * lo que esta query es deliberadamente selectiva pero no evalua fechas.</p>
     *
     * <p>El conjunto es pequeno (catalogo por segmento) y queda acotado al
     * tenant por el filtro global de Hibernate y la RLS (Req 23).</p>
     *
     * @param segmento    segmento de alcance; {@code null} = lista general.
     * @param excluirId   id de la lista a excluir (la propia al editar); puede
     *                    ser {@code null} en el alta (no excluye ninguna).
     * @return las listas activas del mismo alcance, candidatas a solaparse.
     */
    @Query("""
            SELECT l FROM ListaPrecios l
            WHERE l.activo = true
              AND (:excluirId IS NULL OR l.id <> :excluirId)
              AND (
                    (:segmento IS NULL AND l.segmento IS NULL)
                 OR (:segmento IS NOT NULL AND LOWER(l.segmento) = LOWER(:segmento))
              )
            """)
    List<ListaPrecios> buscarActivasDelMismoAlcance(@Param("segmento") String segmento,
                                                    @Param("excluirId") UUID excluirId);
}
