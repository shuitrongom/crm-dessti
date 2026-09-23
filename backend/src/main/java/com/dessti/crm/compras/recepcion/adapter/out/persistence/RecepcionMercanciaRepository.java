package com.dessti.crm.compras.recepcion.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.compras.recepcion.domain.RecepcionMercancia;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link RecepcionMercancia}
 * (Req 32, 23). Replica el patron de {@code OrdenCompraRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como
 * {@link RecepcionMercancia} extiende {@code TenantScopedEntity}, el filtro global
 * de Hibernate {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas
 * consultas al {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V29) lo
 * refuerza. La busqueda por {@code id} de una recepcion de otro tenant devuelve
 * vacio: la capa de aplicacion lo traduce a 404 (Req 23.3).</p>
 */
public interface RecepcionMercanciaRepository extends JpaRepository<RecepcionMercancia, UUID> {

    /**
     * Busca una Recepcion_Mercancia por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la recepcion.
     * @return la recepcion, o vacio (que la aplicacion traduce a 404, Req 23.3).
     */
    Optional<RecepcionMercancia> findById(UUID id);

    /**
     * Listado paginado de Recepciones de Mercancia del tenant vigente con filtro
     * opcional por Orden_Compra (Req 32.7). Un filtro nulo no restringe.
     *
     * @param ordenCompraId Orden_Compra a filtrar; {@code null} no filtra.
     * @param pageable      parametros de paginacion ya acotados (20/100).
     * @return la pagina de recepciones que cumplen el filtro.
     */
    @Query("""
            SELECT r FROM RecepcionMercancia r
            WHERE (:ordenCompraId IS NULL OR r.ordenCompraId = :ordenCompraId)
            """)
    Page<RecepcionMercancia> buscarPorOrdenCompra(
            @Param("ordenCompraId") UUID ordenCompraId, Pageable pageable);

    /**
     * Suma la cantidad recibida ACUMULADA por Partida_Orden_Compra de todas las
     * recepciones registradas para una Orden_Compra dada, en el tenant vigente
     * (Req 32.3, 32.5). Base del calculo del tope acumulado (Property 10) y de la
     * derivacion del estado de la Orden_Compra (Property 11).
     *
     * @param ordenCompraId Orden_Compra cuyas recepciones se agregan.
     * @return una fila por Partida_Orden_Compra con su cantidad recibida acumulada.
     */
    @Query("""
            SELECT p.partidaOrdenCompraId AS partidaOrdenCompraId,
                   SUM(p.cantidadRecibida) AS recibida
            FROM PartidaRecepcion p
            WHERE p.recepcion.ordenCompraId = :ordenCompraId
            GROUP BY p.partidaOrdenCompraId
            """)
    List<RecibidoPorPartida> sumarRecibidoPorPartida(@Param("ordenCompraId") UUID ordenCompraId);

    /**
     * Proyeccion de la cantidad recibida acumulada de una Partida_Orden_Compra.
     */
    interface RecibidoPorPartida {

        /**
         * @return identificador de la Partida_Orden_Compra.
         */
        UUID getPartidaOrdenCompraId();

        /**
         * @return cantidad recibida acumulada de la partida.
         */
        BigDecimal getRecibida();
    }
}
