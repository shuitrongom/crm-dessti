package com.dessti.crm.comercial.producto.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.comercial.producto.domain.PrecioProducto;

/**
 * Repositorio Spring Data JPA de la entidad {@link PrecioProducto} (Req 59, 23).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> las consultas quedan
 * acotadas al tenant vigente por el filtro global de Hibernate (Capa 1) y la RLS
 * (Capa 2, V12).</p>
 */
public interface PrecioProductoRepository extends JpaRepository<PrecioProducto, UUID> {

    /**
     * Busca el precio de un Producto dentro de una Lista_Precios concreta del
     * tenant vigente (unico por {@code (lista, producto)}, Req 59.3).
     *
     * @param listaPreciosId identificador de la Lista_Precios.
     * @param productoId     identificador del Producto.
     * @return el precio, o vacio si no existe.
     */
    Optional<PrecioProducto> findByListaPreciosIdAndProductoId(UUID listaPreciosId, UUID productoId);

    /**
     * Devuelve los precios <strong>vigentes</strong> aplicables a un Producto en
     * la fecha indicada, junto con la prioridad y el segmento de su Lista_Precios,
     * para que la capa de aplicacion aplique la regla de seleccion (Req 59.9).
     *
     * <p>Solo participan los precios cuya Lista_Precios esta activa y vigente a la
     * {@code fecha} (dentro de {@code [vigencia_inicio, vigencia_fin]}, con fin
     * abierto cuando {@code vigencia_fin} es {@code null}). El resultado se ordena
     * por prioridad descendente y, a igualdad, por listas <em>con segmento</em>
     * primero, dejando el desempate final a la capa de aplicacion.</p>
     *
     * @param productoId identificador del Producto.
     * @param fecha      fecha de referencia para la vigencia.
     * @return las candidatas vigentes como proyecciones de seleccion, ordenadas.
     */
    @Query("""
            SELECT new com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository$PrecioVigente(
                       pp.precio, l.prioridad, l.segmento)
            FROM PrecioProducto pp
            JOIN ListaPrecios l ON l.id = pp.listaPreciosId
            WHERE pp.productoId = :productoId
              AND l.activo = true
              AND l.vigenciaInicio <= :fecha
              AND (l.vigenciaFin IS NULL OR l.vigenciaFin >= :fecha)
            ORDER BY l.prioridad DESC,
                     CASE WHEN l.segmento IS NULL THEN 1 ELSE 0 END ASC
            """)
    List<PrecioVigente> buscarPreciosVigentes(@Param("productoId") UUID productoId,
                                              @Param("fecha") LocalDate fecha);

    /**
     * Proyeccion de un precio vigente candidato para la seleccion de precio
     * (Req 59.9): el precio del Producto y la prioridad y el segmento de su
     * Lista_Precios. La regla de desempate (segmento especifico sobre general) la
     * resuelve {@code ServicioSeleccionPrecio}.
     *
     * @param precio    precio unitario del Producto en la lista.
     * @param prioridad prioridad de la Lista_Precios (mayor = antes).
     * @param segmento  segmento de la Lista_Precios; {@code null} = general.
     */
    record PrecioVigente(java.math.BigDecimal precio, int prioridad, String segmento) {
    }

    /**
     * Lista los precios asignados en una Lista_Precios del tenant vigente, junto
     * con el nombre del Producto, para mostrarlos en la UI tras asignar (bugfix:
     * dar visibilidad al precio guardado). Ordenados por nombre de Producto.
     *
     * @param listaPreciosId identificador de la Lista_Precios.
     * @return los precios de la lista como proyecciones (producto + precio).
     */
    @Query("""
            SELECT new com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository$PrecioDeLista(
                       pp.productoId, pr.nombre, pp.precio)
            FROM PrecioProducto pp
            JOIN Producto pr ON pr.id = pp.productoId
            WHERE pp.listaPreciosId = :listaPreciosId
            ORDER BY pr.nombre ASC
            """)
    List<PrecioDeLista> buscarPreciosDeLista(@Param("listaPreciosId") UUID listaPreciosId);

    /**
     * Proyeccion de un precio asignado en una Lista_Precios para mostrarlo en la
     * UI: id y nombre del Producto y su precio.
     *
     * @param productoId     id del Producto.
     * @param productoNombre nombre del Producto.
     * @param precio         precio asignado en la lista.
     */
    record PrecioDeLista(UUID productoId, String productoNombre, java.math.BigDecimal precio) {
    }
}
