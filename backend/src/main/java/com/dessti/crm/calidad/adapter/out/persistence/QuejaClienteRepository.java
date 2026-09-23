package com.dessti.crm.calidad.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.calidad.domain.EstadoQuejaCliente;
import com.dessti.crm.calidad.domain.OrigenQueja;
import com.dessti.crm.calidad.domain.QuejaCliente;

/**
 * Repositorio Spring Data JPA de la raiz del agregado {@link QuejaCliente} (Req 70.1,
 * 23). Provee el listado paginado con filtros (Req 12) y agregaciones de solo lectura
 * para los indicadores de calidad (Req 70.7).
 *
 * <p><strong>Aislamiento multi-tenant (Req 23):</strong> el filtro global de Hibernate
 * y la RLS de PostgreSQL (V47) acotan estas consultas al {@code tenant_id} vigente.</p>
 */
public interface QuejaClienteRepository extends JpaRepository<QuejaCliente, UUID> {

    /**
     * Busca una Queja_Cliente por su identificador dentro del tenant vigente.
     *
     * @param id identificador de la Queja_Cliente.
     * @return la Queja_Cliente, o vacio (404 en la aplicacion, Req 23.3).
     */
    Optional<QuejaCliente> findById(UUID id);

    /**
     * Listado paginado de Quejas del tenant con filtros opcionales por origen, Cliente
     * y estado (Req 70.1, 12). Cada filtro nulo se ignora.
     *
     * @param origen    origen a filtrar; {@code null} no filtra.
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param estado    estado a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados.
     * @return la pagina de Quejas que cumplen los filtros.
     */
    @Query("""
            SELECT q FROM QuejaCliente q
            WHERE (:origen IS NULL OR q.origen = :origen)
              AND (:clienteId IS NULL OR q.clienteId = :clienteId)
              AND (:estado IS NULL OR q.estado = :estado)
            """)
    Page<QuejaCliente> buscar(
            @Param("origen") OrigenQueja origen,
            @Param("clienteId") UUID clienteId,
            @Param("estado") EstadoQuejaCliente estado,
            Pageable pageable);

    /**
     * Cuenta las Quejas del tenant en un estado dado (indicador de calidad, Req 70.7).
     *
     * @param estado estado a contar.
     * @return el numero de Quejas en ese estado.
     */
    long countByEstado(EstadoQuejaCliente estado);
}
