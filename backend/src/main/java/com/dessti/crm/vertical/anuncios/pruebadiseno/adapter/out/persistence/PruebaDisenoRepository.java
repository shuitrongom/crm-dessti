package com.dessti.crm.vertical.anuncios.pruebadiseno.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.EstadoPruebaDiseno;
import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.PruebaDiseno;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link PruebaDiseno}
 * (Req 15, 23). Replica el patron de {@code CotizacionRepository}.
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> como {@link PruebaDiseno}
 * extiende {@code TenantScopedEntity}, el filtro global de Hibernate
 * {@code tenantFilter} (Capa 1) acota <em>automaticamente</em> estas consultas al
 * {@code tenant_id} vigente, y la RLS de PostgreSQL (Capa 2, V16) lo refuerza. La
 * busqueda por {@code id} de una Prueba_Diseno de otro tenant devuelve vacio: la
 * capa de aplicacion lo traduce a 404 y audita el intento (Req 4.3, 23.3).</p>
 *
 * <p><strong>Inmutabilidad del historial (Req 15.4):</strong> el repositorio no
 * expone operaciones que muten filas historicas; el unico cambio permitido es la
 * transicion de estado sobre la version pendiente vigente, aplicada por el
 * dominio y persistida via {@code save}.</p>
 */
public interface PruebaDisenoRepository extends JpaRepository<PruebaDiseno, UUID> {

    /**
     * Busca una Prueba_Diseno por su identificador dentro del tenant vigente. Una
     * prueba inexistente o de otro tenant produce {@link Optional#empty()} (que la
     * aplicacion traduce a 404, Req 23.3).
     *
     * @param id identificador de la Prueba_Diseno.
     * @return la Prueba_Diseno, o vacio.
     */
    Optional<PruebaDiseno> findById(UUID id);

    /**
     * Listado paginado de las Prueba_Diseno de una Cotizacion del tenant vigente
     * (Req 15.6). Se ordena por numero de version descendente para exponer primero
     * la mas reciente. El aislamiento por tenant lo garantizan el filtro global y
     * la RLS (Req 23).
     *
     * @param cotizacionId Cotizacion cuyas pruebas se listan; obligatorio.
     * @param pageable     parametros de paginacion ya acotados (20/100).
     * @return la pagina de Prueba_Diseno de la Cotizacion.
     */
    Page<PruebaDiseno> findByCotizacionIdOrderByNumeroVersionDesc(UUID cotizacionId, Pageable pageable);

    /**
     * Devuelve el maximo numero de version de negocio registrado para una
     * Cotizacion en el tenant vigente, o {@code null} si aun no existe ninguna
     * Prueba_Diseno. La capa de aplicacion suma 1 a este valor para generar la
     * siguiente version al rechazar (Req 15.3, Property 8).
     *
     * @param cotizacionId Cotizacion a consultar.
     * @return el maximo {@code numero_version}, o {@code null} si no hay pruebas.
     */
    @Query("""
            SELECT MAX(p.numeroVersion) FROM PruebaDiseno p
            WHERE p.cotizacionId = :cotizacionId
            """)
    Integer findMaxNumeroVersionByCotizacionId(@Param("cotizacionId") UUID cotizacionId);

    /**
     * Indica si existe al menos una Prueba_Diseno en el estado dado para una
     * Cotizacion en el tenant vigente. Con {@link EstadoPruebaDiseno#APROBADA}
     * implementa la precondicion de la Orden_Fabricacion (Req 15.5, bloque 19).
     *
     * @param cotizacionId Cotizacion a consultar.
     * @param estado       estado a verificar.
     * @return {@code true} si existe alguna prueba en ese estado para la Cotizacion.
     */
    boolean existsByCotizacionIdAndEstado(UUID cotizacionId, EstadoPruebaDiseno estado);

    /**
     * Listado paginado de las Prueba_Diseno pertenecientes a un Cliente en el tenant
     * vigente, mas reciente primero (Portal del Cliente, Req 45.1, 45.6). Una
     * Prueba_Diseno pertenece al Cliente cuando su Cotizacion asociada
     * ({@code cotizacion_id}) es de ese Cliente; el vinculo se resuelve correlando
     * con la {@code Cotizacion} por su identificador.
     *
     * <p><strong>Metodo aditivo de solo lectura (tarea 45.1):</strong> no altera el
     * contrato existente; se anade para dar servicio al Portal sin acoplar los
     * modulos. El aislamiento por tenant lo garantizan el filtro global de Hibernate
     * y la RLS sobre ambas entidades (Req 23). El orden es por
     * {@code numero_version} descendente para exponer primero la version mas
     * reciente, coherente con
     * {@link #findByCotizacionIdOrderByNumeroVersionDesc(UUID, Pageable)}.</p>
     *
     * @param clienteId Cliente cuyas Prueba_Diseno se listan; obligatorio.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Prueba_Diseno del Cliente.
     */
    @Query("""
            SELECT p FROM PruebaDiseno p
            WHERE p.cotizacionId IN (
                SELECT c.id FROM Cotizacion c WHERE c.clienteId = :clienteId
            )
            ORDER BY p.numeroVersion DESC
            """)
    Page<PruebaDiseno> buscarPorClienteOrdenReciente(
            @Param("clienteId") UUID clienteId, Pageable pageable);
}
